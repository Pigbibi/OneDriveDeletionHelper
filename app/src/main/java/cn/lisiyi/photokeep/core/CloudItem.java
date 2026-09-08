package cn.lisiyi.photokeep.core;

public record CloudItem(String id, String name, String path, long size, String etag, boolean folder) {}
