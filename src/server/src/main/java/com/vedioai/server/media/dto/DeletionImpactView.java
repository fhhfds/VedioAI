package com.vedioai.server.media.dto;

import java.util.List;

public record DeletionImpactView(boolean canDelete, List<String> blockers) {
}