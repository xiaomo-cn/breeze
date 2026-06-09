package cn.xiaomo.breeze.knowledge.parser;

import java.io.InputStream;

/**
 * 文档解析器接口——封装文档文本提取逻辑。
 * <p>
 * 当前实现：{@link TikaDocumentParser}（Apache Tika）
 * 未来可扩展：OCR 识别、POI 混合解析等
 * </p>
 *
 * @author Breeze
 */
public interface DocumentParser {

    /**
     * 从输入流中提取文档纯文本内容。
     *
     * @param inputStream 文件输入流
     * @param fileType    文件类型（如 pdf、docx、md），用于解析器优化策略
     * @param fileName    原始文件名，用于日志和错误提示
     * @return 提取的纯文本内容；解析失败时返回 null
     */
    String parse(InputStream inputStream, String fileType, String fileName);
}
