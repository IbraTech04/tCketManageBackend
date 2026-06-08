package com.ibrasoft.tcketmanagebackend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import lombok.AllArgsConstructor;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class TicketGenerationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final int DEFAULT_QR_SIZE = 150;
    private static final String TEMPLATE_PATH = "templates/ticketTemplate.svg";
    private final CryptoService cryptoService;

    private final QRCodeWriter qrCodeWriter = new QRCodeWriter();

    /**
     * Generate QR code as SVG string
     */
    public String generateQRCodeSVG(String data, int size) throws WriterException {
        BitMatrix bitMatrix = generateQRMatrix(data, size);
        return buildSVGString(bitMatrix, size);
    }

    /**
     * Replace text fields and QR code in SVG document
     */
    public void replaceTextFields(Document document, Ticket ticket) throws Exception {
        updateTextElements(document, ticket);
        updateQRCode(document, ticket);
    }

    /**
     * Convert SVG document to PNG file
     */
    public void convertToPng(Document document, String outputPath, Integer width, Integer height) {
        try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
            transcodeToPng(document, outputStream, width, height);
            System.out.println("Successfully converted SVG to PNG: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error converting SVG to PNG: " + e.getMessage());
            throw new RuntimeException("PNG conversion failed", e);
        }
    }

    /**
     * Render a ticket to PNG bytes, ready to be attached to an email. Loads a fresh copy of the
     * SVG template, fills in the ticket's fields and QR code, then transcodes to PNG in-memory.
     */
    public byte[] renderTicketPng(Ticket ticket, Integer width, Integer height) {
        try {
            Document document = loadTemplate();
            replaceTextFields(document, ticket);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            transcodeToPng(document, outputStream, width, height);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render ticket PNG for ticket "
                    + (ticket != null ? ticket.getID() : "null"), e);
        }
    }

    /**
     * Load the ticket SVG template from the classpath into a fresh, namespace-aware DOM document.
     * Each render needs its own copy because {@link #replaceTextFields} mutates the document.
     */
    public Document loadTemplate() throws Exception {
        try (InputStream svgStream = getClass().getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (svgStream == null) {
                throw new IllegalStateException("Ticket template not found on classpath: " + TEMPLATE_PATH);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(svgStream);
        }
    }

    // Private helper methods

    private void transcodeToPng(Document document, OutputStream outputStream, Integer width, Integer height)
            throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();

        if (width != null) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width.floatValue());
        }
        if (height != null) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height.floatValue());
        }

        transcoder.transcode(new TranscoderInput(document), new TranscoderOutput(outputStream));
    }

    private BitMatrix generateQRMatrix(String data, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 0);

        return qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, size, size, hints);
    }

    private String buildSVGString(BitMatrix bitMatrix, int size) {
        StringBuilder svg = new StringBuilder();
        double scale = (double) size / bitMatrix.getWidth();

        svg.append("<g id=\"qrCode\" shape-rendering=\"crispEdges\" transform=\"scale(")
                .append(scale).append(")\">");

        // Generate rectangles for black modules
        for (int y = 0; y < bitMatrix.getHeight(); y++) {
            for (int x = 0; x < bitMatrix.getWidth(); x++) {
                if (bitMatrix.get(x, y)) {
                    svg.append("<rect x=\"").append(x)
                            .append("\" y=\"").append(y)
                            .append("\" width=\"1\" height=\"1\" fill=\"black\"/>");
                }
            }
        }

        svg.append("</g>");
        return svg.toString();
    }

    private void updateTextElements(Document document, Ticket ticket) {
        Map<String, String> fieldMappings = createFieldMappings(ticket);

        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            Element element = getElementById(document, entry.getKey());
            if (element != null && "text".equals(element.getTagName())) {
                element.setTextContent(entry.getValue());
            }
        }
    }

    private void updateQRCode(Document document, Ticket ticket) throws Exception {
        Element qrGroup = getElementById(document, "qrCode");
        if (qrGroup == null) return;

        // Clear existing content
        clearElement(qrGroup);
        TicketQRData data = TicketQRData.fromTicket(ticket);
        String qrData = cryptoService.sign(data);
        BitMatrix bitMatrix = generateQRMatrix(qrData, DEFAULT_QR_SIZE);

        // Set transform and attributes
        qrGroup.setAttribute("transform", "translate(135, 490) scale(" +
                (double) DEFAULT_QR_SIZE / bitMatrix.getWidth() + ")");
        qrGroup.setAttribute("shape-rendering", "crispEdges");

        // Add QR rectangles
        addQRRectangles(document, qrGroup, bitMatrix);
    }

    private void addQRRectangles(Document document, Element parent, BitMatrix bitMatrix) {
        for (int y = 0; y < bitMatrix.getHeight(); y++) {
            for (int x = 0; x < bitMatrix.getWidth(); x++) {
                if (bitMatrix.get(x, y)) {
                    Element rect = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "rect");
                    rect.setAttributeNS(null, "x", String.valueOf(x));
                    rect.setAttributeNS(null, "y", String.valueOf(y));
                    rect.setAttributeNS(null, "width", "1");
                    rect.setAttributeNS(null, "height", "1");
                    rect.setAttributeNS(null, "fill", "black");
                    parent.appendChild(rect);
                }
            }
        }
    }


    private Map<String, String> createFieldMappings(Ticket ticket) {
        Map<String, String> mappings = new HashMap<>();

        mappings.put("ticketId", ticket.getID() != null ? ticket.getID().toString() : "");
        mappings.put("firstName", safeString(ticket.getFirstName()));
        mappings.put("lastName", safeString(ticket.getLastName()));
        mappings.put("email", safeString(ticket.getEmail()));
        mappings.put("fullName", createFullName(ticket));
        mappings.put("ticketType", ticket.getTicketType() != null ? safeString(ticket.getTicketType().getName()) : "");

        if (ticket.getEvent() != null) {
            mappings.put("eventName", safeString(ticket.getEvent().getName()));
            mappings.put("location", safeString(ticket.getEvent().getLocation()));
            mappings.put("eventDescription", safeString(ticket.getEvent().getDescription()));

            LocalDateTime eventTime = ticket.getEvent().getTime();
            mappings.put("eventDate", eventTime != null ? eventTime.format(DATE_FORMAT) : "");
            mappings.put("eventTime", eventTime != null ? eventTime.format(TIME_FORMAT) : "");
        }

        return mappings;
    }

    private String createFullName(Ticket ticket) {
        String firstName = safeString(ticket.getFirstName());
        String lastName = safeString(ticket.getLastName());
        return (firstName + " " + lastName).trim();
    }

    private Element getElementById(Document document, String id) {
        return findElementByAttribute(document.getDocumentElement(), "id", id);
    }

    private Element findElementByAttribute(Element element, String attributeName, String attributeValue) {
        if (attributeValue.equals(element.getAttribute(attributeName))) {
            return element;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element found = findElementByAttribute((Element) child, attributeName, attributeValue);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private void clearElement(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }
}