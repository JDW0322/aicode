package com.dw.daicodemother.ai.model;

import jdk.jfr.Description;
import lombok.Data;

@Description("HTML代码结果")
@Data
public class HtmlCodeResult {

    @Description("HTML代码")
    private String htmlCode;

    @Description("生成代码的描述")
    private String description;
}

