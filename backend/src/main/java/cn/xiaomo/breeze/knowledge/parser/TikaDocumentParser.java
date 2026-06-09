package cn.xiaomo.breeze.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 基于 Apache Tika 的文档解析器实现。
 * <p>
 * 支持 PDF、DOCX、XLSX、PPTX、MD、TXT、HTML、CSV、PNG、JPG 等常见格式。
 * 对于图片文件，Tika 仅能提取元数据文本，无法做 OCR 识别。
 * </p>
 *
 * @author Breeze
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private static final Tika TIKA = new Tika();

    @Override
    public String parse(InputStream inputStream, String fileType, String fileName) {
        try {
            log.debug("Tika 开始解析: {} (type={})", fileName, fileType);
            String text = TIKA.parseToString(inputStream, new Metadata());
            log.debug("Tika 解析完成: {}, 文本长度={}", fileName, text != null ? text.length() : 0);
            return text;
        } catch (Exception e) {
            log.warn("Tika 解析失败: {} (type={})", fileName, fileType, e);
            return null;
        }
    }
}
