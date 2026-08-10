package com.lzcer.interfaceplatform.sqlquery;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReadOnlySqlValidator {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|merge|replace|drop|alter|create|truncate|grant|revoke|call|exec|execute|load|outfile|dumpfile|for\\s+update|lock)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAMETER = Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");

    public ValidatedSql validate(String rawSql) {
        String sql = rawSql == null ? "" : rawSql.strip();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).stripTrailing();
        }
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (sql.isBlank() || !normalized.startsWith("select ")) {
            throw invalid("只允许单条 SELECT 查询");
        }
        // 不支持注释和多语句，避免通过方言、注释拼接绕过只读关键字检查。
        if (sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("*/")
                || FORBIDDEN.matcher(sql).find()) {
            throw invalid("SQL 包含注释、多语句或写操作关键字");
        }
        Set<String> parameters = new LinkedHashSet<>();
        // SQL 只允许管理员预先配置；调用方仅能提供 :name 形式的参数值，不能提交 SQL 片段。
        Matcher matcher = PARAMETER.matcher(sql);
        while (matcher.find()) parameters.add(matcher.group(1));
        return new ValidatedSql(sql, parameters);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "IP-SQL-001", message);
    }

    public record ValidatedSql(String sql, Set<String> parameters) {}
}
