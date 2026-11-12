package com.middleware.alert.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 极简 JSON 序列化，仅用于 {@code extra} 字段，避免向使用方强制传递 Jackson。
 *
 * <p>支持 null、String、Number、Boolean、Map、Collection、数组，其余类型按字符串处理。</p>
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder(64);
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String || value instanceof Character) {
            appendString(sb, String.valueOf(value));
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            appendMap(sb, (Map<?, ?>) value);
        } else if (value instanceof Collection) {
            appendCollection(sb, (Collection<?>) value);
        } else if (value.getClass().isArray()) {
            appendArray(sb, value);
        } else {
            appendString(sb, String.valueOf(value));
        }
    }

    private static void appendMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            append(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void appendCollection(StringBuilder sb, Collection<?> collection) {
        sb.append('[');
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            append(sb, item);
        }
        sb.append(']');
    }

    private static void appendArray(StringBuilder sb, Object array) {
        sb.append('[');
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            append(sb, Array.get(array, i));
        }
        sb.append(']');
    }

    private static void appendString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
