package com.liushao.vueadmin;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

/** Explicitly invoked developer utility. Output is isolated from application sources. */
public class CodeGenerator {
    public static void main(String[] tables) {
        if (tables.length == 0) throw new IllegalArgumentException("Pass the table names to generate");
        FastAutoGenerator.create(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
            .globalConfig(builder -> builder.author("liushao").disableOpenDir()
                .outputDir(System.getProperty("user.dir") + "/target/generated-code"))
            .packageConfig(builder -> builder.parent("com.liushao.vueadmin"))
            .strategyConfig(builder -> builder.addInclude(tables))
            .templateEngine(new FreemarkerTemplateEngine())
            .execute();
    }
}
