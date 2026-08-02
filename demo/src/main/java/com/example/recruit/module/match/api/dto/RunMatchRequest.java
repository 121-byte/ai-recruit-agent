package com.example.recruit.module.match.api.dto;

import java.util.Map;

public class RunMatchRequest {

    private Map<String, Double> weights;

    public Map<String, Double> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, Double> weights) {
        this.weights = weights;
    }
}
