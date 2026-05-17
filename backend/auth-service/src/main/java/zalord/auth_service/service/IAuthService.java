package zalord.auth_service.service;

import zalord.auth_service.dto.response.LoginResponse;
import zalord.auth_service.dto.response.RegisterResponse;

public interface IAuthService {

    RegisterResponse register(String displayName, String phoneNumber, String password);

    RegisterResponse createAdmin(String displayName, String phoneNumber, String password);

    LoginResponse login(String phoneNumber, String password);
}
