package com.loadBalance.robin;

public class Weight {

    private Integer weight;
    private Integer currentWeight;
    private String ip;

    public Weight(Integer weight, Integer currentWeight, String ip) {
        this.weight = weight;
        this.currentWeight = currentWeight;
        this.ip = ip;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Integer getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(Integer currentWeight) {
        this.currentWeight = currentWeight;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
