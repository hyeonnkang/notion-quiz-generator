package com.notionquiz.generator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DocumentChunkService {

    private static final int MIN_CHUNK_SIZE = 1200;
    private static final int IDEAL_CHUNK_SIZE = 1300;
    private static final int MAX_CHUNK_SIZE = 1500;

    public List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isBlank()) {
            return Collections.emptyList();
        }

        String[] paragraphs = normalized.split("\\n{2,}|\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isBlank()) {
                continue;
            }

            if (trimmedParagraph.length() > MAX_CHUNK_SIZE) {
                addChunk(chunks, current.toString());
                current.setLength(0);
                splitLongParagraph(trimmedParagraph, chunks);
                continue;
            }

            appendParagraph(current, trimmedParagraph, chunks);
        }

        addChunk(chunks, current.toString());
        return chunks;
    }

    private void appendParagraph(StringBuilder current, String paragraph, List<String> chunks) {
        if (current.length() == 0) {
            current.append(paragraph);
            return;
        }

        int candidateLength = current.length() + 1 + paragraph.length();
        if (candidateLength <= IDEAL_CHUNK_SIZE ||
            (candidateLength <= MAX_CHUNK_SIZE && current.length() < MIN_CHUNK_SIZE)) {
            current.append('\n').append(paragraph);
            return;
        }

        addChunk(chunks, current.toString());
        current.setLength(0);
        current.append(paragraph);
    }

    private void splitLongParagraph(String paragraph, List<String> chunks) {
        String[] sentences = paragraph.split("(?<=[.!?。！？])\\s+");
        StringBuilder part = new StringBuilder();

        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            if (trimmedSentence.isBlank()) {
                continue;
            }

            if (trimmedSentence.length() > MAX_CHUNK_SIZE) {
                addChunk(chunks, part.toString());
                part.setLength(0);
                splitByFixedLength(trimmedSentence, chunks);
                continue;
            }

            if (part.length() == 0) {
                part.append(trimmedSentence);
                continue;
            }

            int candidateLength = part.length() + 1 + trimmedSentence.length();
            if (candidateLength <= IDEAL_CHUNK_SIZE ||
                (candidateLength <= MAX_CHUNK_SIZE && part.length() < MIN_CHUNK_SIZE)) {
                part.append(' ').append(trimmedSentence);
                continue;
            }

            addChunk(chunks, part.toString());
            part.setLength(0);
            part.append(trimmedSentence);
        }

        addChunk(chunks, part.toString());
    }

    private void splitByFixedLength(String text, List<String> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            addChunk(chunks, text.substring(start, end));
            start = end;
        }
    }

    private void addChunk(List<String> chunks, String chunk) {
        if (chunk == null) {
            return;
        }
        String trimmed = chunk.trim();
        if (!trimmed.isBlank()) {
            chunks.add(trimmed);
        }
    }
}
