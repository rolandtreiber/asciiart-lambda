package asciiart;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.alcibiade.asciiart.coord.TextBoxSize;
import org.alcibiade.asciiart.image.rasterize.ShapeRasterizer;
import org.alcibiade.asciiart.raster.ExtensibleCharacterRaster;
import org.alcibiade.asciiart.raster.Raster;
import org.alcibiade.asciiart.raster.RasterContext;
import org.alcibiade.asciiart.widget.PictureWidget;
import org.alcibiade.asciiart.widget.TextWidget;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.MultipartStream;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.imageio.ImageIO;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public static final String bucketName = "asciiart-app";
    public static final Region region = Region.EU_WEST_2;
    public boolean inverted = false;
    public Color color = Color.WHITE;
    public float fontSize = 22f;
    public int asciiHeight = 0;

    public String fileName = "";

    public static void uploadToS3(String objectPath, String objectKey) {
        S3Client s3 = S3Client.builder()
                .region(region)
                .build();

        putS3Object(s3, bucketName, objectKey, objectPath);
        s3.close();
    }

    public static void putS3Object(S3Client s3, String bucketName, String objectKey, String objectPath) {
        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3.putObject(putOb, RequestBody.fromFile(new File(objectPath)));

        } catch (S3Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static File getS3Object(String objectKey) {
        try {
            S3Client s3 = S3Client.builder()
                    .region(region)
                    .build();

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            ResponseInputStream<GetObjectResponse> o = s3.getObject(getObjectRequest);
            FileOutputStream fos = new FileOutputStream("/tmp/" + objectKey);

            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = o.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            o.close();
            fos.close();
            return new File("/tmp/" + objectKey);

        } catch (S3Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public String getPresignedUrl(String objectKey) {
        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))  // The URL will expire in 10 minutes.
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toExternalForm();
        }
    }

    private String generateRandomString(int length) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomLimitedInt = leftLimit + (int)
                    (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

    public void exportToImage(String text, String fileName, int asciiWidth, Color colour) throws IOException, FontFormatException {
        String[] str = text.split("\n");
        System.out.println(str[0]);
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = img.createGraphics();

        Font font = Font.createFont(Font.TRUETYPE_FONT, getS3Object("VeraMono.ttf")).deriveFont(fontSize);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        int width = (fm.stringWidth("X")) * asciiWidth;
        img = new BufferedImage(width, str.length * ((int) fontSize), BufferedImage.TYPE_INT_ARGB);
        int i = 0;
        for (String line : str) {
            g2d.dispose();
            g2d = img.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setFont(font);
            fm = g2d.getFontMetrics();
            g2d.setColor(colour);
            g2d.drawString(line, 0, (fm.getAscent() + 1) * i);
            g2d.dispose();
            i++;
        }
        try {
            ImageIO.write(img, "png", new File("/tmp/" + fileName + ".png"));
            uploadToS3("/tmp/" + fileName + ".png", fileName + ".png");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    public void processImage(BufferedImage bufferedImage, int asciiHeight, String fileName) throws IOException, FontFormatException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        // Convert to negative
        if (inverted) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int p = bufferedImage.getRGB(x, y);
                    int a = (p >> 24) & 0xff;
                    int r = (p >> 16) & 0xff;
                    int g = (p >> 8) & 0xff;
                    int b = p & 0xff;

                    // subtract RGB from 255
                    r = 255 - r;
                    g = 255 - g;
                    b = 255 - b;

                    // set new RGB value
                    p = (a << 24) | (r << 16) | (g << 8) | b;
                    bufferedImage.setRGB(x, y, p);
                }
            }
        }

        if (asciiHeight == 0) {
            double h = ((double) height / (fontSize + 1));
            asciiHeight = (int) h;
        }
        double asciiWidth = (((double) width / height) * asciiHeight) * 1.85;

        TextWidget widget = new PictureWidget(new TextBoxSize((int) asciiWidth, asciiHeight),
                bufferedImage, new ShapeRasterizer());
        Raster raster = new ExtensibleCharacterRaster();

        widget.render(new RasterContext(raster));

        exportToImage(raster.toString(), fileName, (int) asciiWidth, color);

        try (PrintWriter out = new PrintWriter("/tmp/" + fileName + ".txt")) {
            out.println(raster);
        }

    }

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        LambdaLogger logger = context.getLogger();
        fileName = generateRandomString(14);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        logger.log("Loading Java Lambda handler of Proxy");

        logger.log(String.valueOf(input.getBody().getBytes().length));

        String contentType = "";
        try {
            //Get the uploaded file and decode from base64
            byte[] bI = Base64.decodeBase64(input.getBody().getBytes());

            //Get the content-type header
            Map<String, String> hps = input.getHeaders();

            if (hps != null) {
                contentType = hps.get("Content-Type");
            }
            logger.log("Content Type: " + contentType);

            //Extract the boundary
            String[] boundaryArray = contentType.split("=");

            //Transform the boundary to a byte array
            byte[] boundary = boundaryArray[1].getBytes();

            MultipartStream multipartStream = new MultipartStream(
                    new ByteArrayInputStream(bI),
                    boundary,
                    1024,
                    null);

            boolean nextPart = multipartStream.skipPreamble();
            BufferedImage bufferedImage = null;

            // Resetting values
            inverted = false;
            color = Color.BLACK;
            asciiHeight = 0;
            fontSize = 22;
            while (nextPart) {

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                String partHeaders = multipartStream.readHeaders();
                multipartStream.readBodyData(output);

                if (partHeaders.contains("name=\"inverted\"")) {
                    logger.log("Setting the inverted to true");
                    if (output.toString().equals("1")) {
                        logger.log("Setting the inverted to true");
                        inverted = true;
                    }
                }

                if (partHeaders.contains("name=\"color\"")) {
                    logger.log("Setting the color to " + output);
                    switch (output.toString()) {
                        case "blue":
                            color = Color.BLUE;
                            break;
                        case "green":
                            color = Color.GREEN;
                            break;
                        case "black":
                            color = Color.BLACK;
                            break;
                        case "white":
                            color = Color.WHITE;
                            break;
                        case "yellow":
                            color = Color.YELLOW;
                            break;
                        case "red":
                            color = Color.RED;
                            break;
                        case "dark-gray":
                            color = Color.DARK_GRAY;
                            break;
                        case "light-gray":
                            color = Color.LIGHT_GRAY;
                            break;
                    }
                }

                if (partHeaders.contains("name=\"fontSize\"")) {
                    logger.log("Setting the font size to " + output);
                    fontSize = Float.parseFloat(output.toString());
                }

                if (partHeaders.contains("name=\"height\"")) {
                    logger.log("Setting the height to " + output);
                    asciiHeight = Integer.parseInt(output.toString());
                }

                if (partHeaders.contains("name=\"image\"")) {
                    try (OutputStream outputStream = new FileOutputStream("/tmp/tmp.jpg")) {
                        output.writeTo(outputStream);
                        logger.log("File written");
                        File image = new File("/tmp/tmp.jpg");
                        bufferedImage = ImageIO.read(image);
                    }
                }

                logger.log(partHeaders);
                nextPart = multipartStream.readBoundary();
            }

            if (bufferedImage != null) {
                processImage(bufferedImage, asciiHeight, fileName);
            }

            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                    .withHeaders(headers);

            String presignedUrl = getPresignedUrl(fileName+".png");
            String output = "{ " +
                    "\"message\": " + "\"success\"}" +
                    "\"url\": " + "\"" + presignedUrl + "\"}";

            return response
                    .withStatusCode(200)
                    .withBody(output);

        } catch (IOException | FontFormatException e) {
            throw new RuntimeException(e);
        }
    }
}
