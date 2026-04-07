package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightSeriesDto {
    private String name;
    /** 柱状/折线/饼（单系列时与 categories 一一对应） */
    private List<Double> data;
}
