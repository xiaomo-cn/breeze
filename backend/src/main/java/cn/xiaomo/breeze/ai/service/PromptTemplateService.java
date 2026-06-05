package cn.xiaomo.breeze.ai.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;

/**
 * 统一的 AI 提示词模板加载和渲染服务。
 *
 * 启动时自动扫描 classpath:prompts/*.st，将每个 .st 文件编译为 StringTemplate。
 * 模板使用 $...$ 分隔符，避免与 LLM 输出中的 HTML/XML/JSON 语法冲突。
 */
@Slf4j
@Service
public class PromptTemplateService {

    private final ResourceLoader resourceLoader;

    /** 模板源码缓存，key = 文件名（不含 .st 后缀） */
    private final Map<String, String> templateSources = new ConcurrentHashMap<>();

    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() throws IOException {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(resourceLoader);

        // 扫描 prompts/ 根目录
        loadFromPattern(resolver, "classpath:prompts/*.st", "");
        // 扫描 prompts/tools/ 子目录
        loadFromPattern(resolver, "classpath:prompts/tools/*.st", "tools/");

        log.info("已加载 {} 个提示词模板（classpath:prompts/）", templateSources.size());
    }

    private void loadFromPattern(PathMatchingResourcePatternResolver resolver,
                                  String pattern, String prefix) throws IOException {
        Resource[] resources = resolver.getResources(pattern);
        for (Resource r : resources) {
            String filename = r.getFilename();
            if (filename == null) continue;
            String name = prefix + filename.substring(0, filename.length() - 3); // 去掉 .st 后缀
            String source = r.getContentAsString(StandardCharsets.UTF_8);
            templateSources.put(name, source);
            log.debug("已加载提示词模板: {}", name);
        }
    }

    /**
     * 渲染指定模板。
     *
     * @param name      模板名（文件名不含 .st 后缀，如 "system-prompt"）
     * @param variables 模板变量键值对
     * @return 渲染后的提示词文本
     */
    public String render(String name, Map<String, Object> variables) {
        String source = templateSources.get(name);
        if (source == null) {
            throw new IllegalArgumentException("提示词模板未找到: " + name);
        }
        ST st = new ST(source, '$', '$');
        if (variables != null) {
            for (var entry : variables.entrySet()) {
                st.add(entry.getKey(), entry.getValue());
            }
        }
        return st.render();
    }

    /**
     * 渲染无变量模板。
     *
     * @param name 模板名
     * @return 渲染后的文本
     */
    public String render(String name) {
        return render(name, Map.of());
    }

    /**
     * 开发时热重载所有模板。
     */
    public void reload() throws IOException {
        templateSources.clear();
        init();
    }
}
