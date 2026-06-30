package zalord.auth_service.dto.response;

import lombok.Builder;

@Builder
public record LoginResponse(
		String accessToken,
		String refreshToken
	) {
		public String getAccessToken() { return accessToken(); }
		public String getRefreshToken() { return refreshToken(); }
	
}
