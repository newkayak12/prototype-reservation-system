package com.example.config.i18n

import com.example.config.persistence.PersistenceConfig
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.yaml.snakeyaml.Yaml
import java.util.*

@Configuration
internal class MessageSourceConfig {


    @Bean
    fun messageSource(): MessageSource = YamlMessageSource()
        .apply {
            setBasenames("classpath:messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
        }
}

private class YamlMessageSource : ResourceBundleMessageSource() {
    override fun doGetBundle(basename: String, locale: Locale): ResourceBundle =
        ResourceBundle.getBundle(basename, locale, YamlConverter());
}

private class YamlConverter : ResourceBundle.Control() {
    override fun getFormats(baseName: String?): MutableList<String> = mutableListOf("yaml", "yml")
    override fun newBundle(
        baseName: String?, locale: Locale?, format: String?,
        loader: ClassLoader?, reload: Boolean
    ): ResourceBundle? {
        val resourceName = toResourceName(toBundleName(baseName, locale), format)
        val inputStream = loader?.getResourceAsStream(resourceName) ?: return null


        val yaml = Yaml();
        val data: Map<String, Any> = yaml.load(inputStream)

        return object : ResourceBundle() {
            override fun handleGetObject(key: String): Any? = data[key]
            override fun getKeys(): Enumeration<String> = Collections.enumeration(data.keys)
        }
    }

}