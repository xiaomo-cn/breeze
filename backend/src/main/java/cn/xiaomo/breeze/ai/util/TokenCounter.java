package cn.xiaomo.breeze.ai.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

/**
 * Token 计数工具，基于 JTokkit BPE tokenizer（CL100K_BASE 编码）。
 * <p>
 * 使用 OpenAI cl100k_base 编码近似 DeepSeek 的 tokenizer，
 * 精度远高于字符级启发式估算（中文/1.8 + 英文/3.5）。
 */
@Component
public class TokenCounter {

    private final Encoding encoding;

    public TokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * 计算文本的 token 数量。
     *
     * @param text 待计数的文本
     * @return BPE token 数量
     */
    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /**
     * 计算多条消息的总 token 数。
     *
     * @param messages 消息文本列表
     * @return 总 token 数量
     */
    public int estimateAll(String... messages) {
        int total = 0;
        for (String msg : messages) {
            total += estimate(msg);
        }
        return total;
    }
}
