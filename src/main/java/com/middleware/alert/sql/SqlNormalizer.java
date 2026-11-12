package com.middleware.alert.sql;

import java.util.regex.Pattern;

/**
 * SQL 归一化：把字面量与可变结构抹平，使同一类语句得到稳定指纹。
 *
 * <p>MyBatis 的 {@code BoundSql} 本身已用 {@code ?} 占位，本类主要处理
 * {@code ${}} 拼接出来的字面量、注释与 {@code IN (?,?,?)} 的个数差异。</p>
 */
public final class SqlNormalizer {

    /** 单引号字符串字面量，处理 '' 转义 */
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    /** 行注释 */
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n\\r]*");
    /** 块注释 */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    /** 数字字面量：不碰标识符（如 t1）与已有占位符 */
    private static final Pattern NUMBER_LITERAL =
            Pattern.compile("(?<![\\w?])\\d+(?:\\.\\d+)?(?![\\w?])");
    /** 参数列表归一化：(?, ?, ?) → (?) */
    private static final Pattern PARAM_LIST =
            Pattern.compile("\\(\\s*\\?(?:\\s*,\\s*\\?)+\\s*\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SqlNormalizer() {
    }

    /**
     * 归一化 SQL。null 原样返回，空串返回空串。
     */
    public static String normalize(String sql) {
        if (sql == null) {
            return null;
        }
        String text = sql;
        text = STRING_LITERAL.matcher(text).replaceAll("?");
        text = LINE_COMMENT.matcher(text).replaceAll(" ");
        text = BLOCK_COMMENT.matcher(text).replaceAll(" ");
        text = NUMBER_LITERAL.matcher(text).replaceAll("?");
        text = PARAM_LIST.matcher(text).replaceAll("(?)");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        return text;
    }
}
