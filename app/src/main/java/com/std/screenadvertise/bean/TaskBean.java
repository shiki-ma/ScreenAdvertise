package com.std.screenadvertise.bean;

import java.util.List;

/**
 * Created by Maik on 2016/6/9.
 */
public class TaskBean {
    private String style;
    private List<MediaBean> videoList;
    private List<MediaBean> picList;

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public List<MediaBean> getVideoList() {
        return videoList;
    }

    public void setVideoList(List<MediaBean> videoList) {
        this.videoList = videoList;
    }

    public List<MediaBean> getPicList() {
        return picList;
    }

    public void setPicList(List<MediaBean> picList) {
        this.picList = picList;
    }
}
