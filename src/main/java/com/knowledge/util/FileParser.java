package com.knowledge.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FileParser {

    /**
     * 从上传文件中提取纯文本内容，支持 PDF / DOCX / TXT
     */
    public String extractText(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() != null
            ? file.getOriginalFilename().toLowerCase()
            : "";

        if (isPdf(contentType, filename)) {
            return extractPdf(file);
        } else if (isWord(contentType, filename)) {
            return extractDocx(file);
        } else if (isText(contentType, filename)) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("不支持的文件类型，请上传 PDF、Word 或 TXT 文件");
        }
    }

    /**
     * 从已读取的字节数组中提取文本（用于异步任务，避免流关闭问题）
     */
    public String extractText(byte[] bytes, String contentType, String filename) throws IOException {
        String fn = filename != null ? filename.toLowerCase() : "";
        if (isPdf(contentType, fn)) {
            try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(bytes)))) {
                return new PDFTextStripper().getText(document).trim();
            }
        } else if (isWord(contentType, fn)) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText().trim();
            }
        } else if (isText(contentType, fn)) {
            return new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("不支持的文件类型，请上传 PDF、Word 或 TXT 文件");
        }
    }

    private boolean isPdf(String contentType, String filename) {
        return (contentType != null && contentType.contains("pdf")) || filename.endsWith(".pdf");
    }

    private boolean isWord(String contentType, String filename) {
        return (contentType != null && (contentType.contains("word") || contentType.contains("officedocument")))
            || filename.endsWith(".docx") || filename.endsWith(".doc");
    }

    private boolean isText(String contentType, String filename) {
        return (contentType != null && contentType.contains("text")) || filename.endsWith(".txt");
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))) {
            return new PDFTextStripper().getText(document).trim();
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().trim();
        }
    }
}
