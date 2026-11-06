package com.leon.simpleeditor;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * 文本文件的编码判断：优先严格 UTF-8，失败退回 GB18030（兼容 GBK / GB2312）。
 */
final class TextCodec {

    static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private TextCodec() {
    }

    /** 头部出现 0 字节，基本可以认为是二进制文件。 */
    static boolean looksBinary(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }

    static boolean hasUtf8Bom(byte[] data) {
        return data.length >= 3
                && data[0] == UTF8_BOM[0]
                && data[1] == UTF8_BOM[1]
                && data[2] == UTF8_BOM[2];
    }

    static Charset detectCharset(byte[] data, int offset, int length) {
        if (length <= 0) {
            return Charset.forName("UTF-8");
        }
        if (isValidUtf8(data, offset, length)) {
            return Charset.forName("UTF-8");
        }
        try {
            return Charset.forName("GB18030");
        } catch (Exception e) {
            return Charset.forName("UTF-8");
        }
    }

    private static boolean isValidUtf8(byte[] data, int offset, int length) {
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data, offset, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
