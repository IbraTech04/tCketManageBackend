package com.ibrasoft.tcketmanagebackend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import lombok.AllArgsConstructor;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a {@link Ticket} into a PNG by populating the {@code ticket-c-midnight} SVG template with
 * Thymeleaf and transcoding the result with Batik.
 *
 * <p>The dynamic QR code is the one part that can't be expressed as a plain Thymeleaf variable: its
 * module grid is generated here as an SVG {@code <g>} fragment and injected into the template via
 * {@code th:utext} (unescaped), so the markup lands in the output verbatim for Batik to rasterize.
 */
@Service
@AllArgsConstructor
public class TicketGenerationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE · MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final String TEMPLATE_NAME = "ticket-c-midnight";

    /** Module resolution ZXing rasterizes the QR to; higher means more, smaller rects. */
    private static final int QR_MATRIX_SIZE = 150;
    /** Dark module colour, matched to the template's card background. */
    private static final String QR_MODULE_FILL = "#17171c";
    // Placement of the QR inside the template's white panel (x=66 y=450 w=228 h=222).
    private static final double QR_RENDER_SIZE = 184d;
    private static final double QR_TRANSLATE_X = 66 + (228 - QR_RENDER_SIZE) / 2; // centred horizontally
    private static final double QR_TRANSLATE_Y = 466;

    private final CryptoService cryptoService;
    private final TemplateEngine svgTemplateEngine;

    private final QRCodeWriter qrCodeWriter = new QRCodeWriter();

    /**
     * Render a ticket to PNG bytes, ready to be attached to an email. Fills the SVG template with the
     * ticket's fields and signed QR code, then transcodes to PNG in-memory.
     */
    public byte[] renderTicketPng(Ticket ticket, Integer width, Integer height) {
        try {
            String svg = renderTicketSvg(ticket);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            transcodeToPng(svg, outputStream, width, height);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render ticket PNG for ticket "
                    + (ticket != null ? ticket.getID() : "null"), e);
        }
    }

    /**
     * Render the ticket SVG as a string by processing the Thymeleaf template with the ticket's data.
     */
    public String renderTicketSvg(Ticket ticket) throws Exception {
        return svgTemplateEngine.process(TEMPLATE_NAME, buildContext(ticket));
    }

    /**
     * Generate the signed QR code for a ticket as a self-contained, positioned SVG {@code <g>}
     * fragment (its own {@code transform} sizes and places it within the template's QR panel).
     */
    public String generateQRCodeSVG(Ticket ticket) throws Exception {
        TicketQRData data = TicketQRData.fromTicket(ticket);
        String token = cryptoService.sign(data);
        return buildQrGroup(generateQRMatrix(token, QR_MATRIX_SIZE));
    }

    // Private helper methods

    private Context buildContext(Ticket ticket) throws Exception {
        Event event = ticket.getEvent();
        LocalDateTime time = event != null ? event.getTime() : null;

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("ticketType",
                ticket.getTicketType() != null ? safeString(ticket.getTicketType().getName()) : "");
        context.setVariable("fullName", createFullName(ticket));
        context.setVariable("eventName", event != null ? safeString(event.getName()) : "");
        context.setVariable("location", event != null ? safeString(event.getLocation()) : "");
        context.setVariable("locationDetail", event != null ? safeString(event.getDescription()) : "");
        context.setVariable("eventDate", time != null ? time.format(DATE_FORMAT) : "");
        context.setVariable("eventTime", time != null ? time.format(TIME_FORMAT) : "");
        context.setVariable("qrCode", generateQRCodeSVG(ticket));
        return context;
    }

    private void transcodeToPng(String svg, OutputStream outputStream, Integer width, Integer height)
            throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();

        if (width != null) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width.floatValue());
        }
        if (height != null) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height.floatValue());
        }

        transcoder.transcode(new TranscoderInput(new StringReader(svg)), new TranscoderOutput(outputStream));
    }

    private BitMatrix generateQRMatrix(String data, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 0);

        return qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, size, size, hints);
    }

    private String buildQrGroup(BitMatrix bitMatrix) {
        double scale = QR_RENDER_SIZE / bitMatrix.getWidth();

        StringBuilder svg = new StringBuilder();
        svg.append("<g shape-rendering=\"crispEdges\" transform=\"translate(")
                .append(QR_TRANSLATE_X).append(' ').append(QR_TRANSLATE_Y)
                .append(") scale(").append(scale).append(")\">");

        for (int y = 0; y < bitMatrix.getHeight(); y++) {
            for (int x = 0; x < bitMatrix.getWidth(); x++) {
                if (bitMatrix.get(x, y)) {
                    svg.append("<rect x=\"").append(x)
                            .append("\" y=\"").append(y)
                            .append("\" width=\"1\" height=\"1\" fill=\"").append(QR_MODULE_FILL).append("\"/>");
                }
            }
        }

        svg.append("</g>");
        return svg.toString();
    }

    private String createFullName(Ticket ticket) {
        String firstName = safeString(ticket.getFirstName());
        String lastName = safeString(ticket.getLastName());
        return (firstName + " " + lastName).trim();
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }
}
