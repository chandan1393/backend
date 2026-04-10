package com.assignease.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailRequest {

    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;
}