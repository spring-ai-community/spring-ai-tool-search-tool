package org.springaicommunity.tool.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolReference(String toolName, Double relevanceScore, String summary) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String toolName;

		private Double relevanceScore;

		private String summary;

		public Builder toolName(String toolName) {
			this.toolName = toolName;
			return this;
		}

		public Builder relevanceScore(Double relevanceScore) {
			this.relevanceScore = relevanceScore;
			return this;
		}

		public Builder relevanceScore(Float relevanceScore) {
			this.relevanceScore = relevanceScore.doubleValue();
			return this;
		}

		public Builder summary(String summary) {
			this.summary = summary;
			return this;
		}

		public ToolReference build() {
			return new ToolReference(this.toolName, this.relevanceScore, this.summary);
		}

	}
}