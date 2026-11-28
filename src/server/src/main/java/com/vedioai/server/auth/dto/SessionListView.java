package com.vedioai.server.auth.dto;

import java.util.List;


public record SessionListView(List<SessionItemView> items) {
}
