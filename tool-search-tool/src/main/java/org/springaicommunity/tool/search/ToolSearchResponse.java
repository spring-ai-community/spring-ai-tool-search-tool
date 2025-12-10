/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.tool.search;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @author Christian Tzolov
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolSearchResponse(List<ToolReference> toolReferences, Integer totalMatches,
		SearchMetadata searchMetadata) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<ToolReference> toolReferences = new ArrayList<>();

		private Integer totalMatches;

		private SearchMetadata searchMetadata;

		public Builder toolReferences(List<ToolReference> toolReferences) {
			this.toolReferences = toolReferences;
			return this;
		}

		public Builder addToolReference(ToolReference toolReference) {
			this.toolReferences.add(toolReference);
			return this;
		}

		public Builder totalMatches(Integer totalMatches) {
			this.totalMatches = totalMatches;
			return this;
		}

		public Builder searchMetadata(SearchMetadata searchMetadata) {
			this.searchMetadata = searchMetadata;
			return this;
		}

		public ToolSearchResponse build() {
			return new ToolSearchResponse(this.toolReferences, this.totalMatches, this.searchMetadata);
		}

	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SearchMetadata(SearchType searchType, String query, Long searchTimeMs) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private SearchType searchType;

			private String query;

			private Long searchTimeMs;

			public Builder searchType(SearchType searchType) {
				this.searchType = searchType;
				return this;
			}

			public Builder query(String query) {
				this.query = query;
				return this;
			}

			public Builder searchTimeMs(Long searchTimeMs) {
				this.searchTimeMs = searchTimeMs;
				return this;
			}

			public SearchMetadata build() {
				return new SearchMetadata(this.searchType, this.query, this.searchTimeMs);
			}

		}
	}
}
