package com.ssafy.keeping.domain.auth.handler;


import com.ssafy.keeping.domain.auth.Util.CookieUtil;
import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.enums.UserRole;
import com.ssafy.keeping.domain.auth.service.AuthService;
import com.ssafy.keeping.domain.auth.service.TokenResponse;
import com.ssafy.keeping.domain.auth.service.TokenService;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.service.CustomerService;
import com.ssafy.keeping.domain.user.owner.model.Owner;
import com.ssafy.keeping.domain.user.owner.service.OwnerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final TokenService tokenService;
    private final CookieUtil cookieUtil;
    private final CustomerService customerService;
    private final OwnerService ownerService;

    // 추후 환경변수로 저장
    @Value("${fe.base-url}")
    private String feBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        System.out.println("=== OAUTH SUCCESS HANDLER START ===");
        System.out.println("[OAUTH SUCCESS] Request URI: " + request.getRequestURI());
        System.out.println("[OAUTH SUCCESS] Query String: " + request.getQueryString());
        System.out.println("[OAUTH SUCCESS] State parameter: " + request.getParameter("state"));
        System.out.println("[OAUTH SUCCESS] Code parameter: " + request.getParameter("code"));

        // role 복원
        UserRole role = authService.extractRoleFromState(   request.getParameter("state"));
        System.out.println("[OAUTH SUCCESS] Extracted role: " + role);

        // role이 null인 경우 처리
        if (role == null) {
            System.out.println("[OAUTH] Role is null - redirecting to role selection");

            if (devFallback()) {
                // 개발 환경에서는 HTML 페이지로 응답 (JSON 대신)
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(createErrorHtmlPage(
                        "Role Selection Required",
                        "OAuth 로그인은 성공했지만 역할(role) 정보가 없습니다.",
                        "다시 로그인해주세요: <a href='/oauth-test'>OAuth 테스트 페이지</a>"
                ));
                return;
            }

            // 프론트엔드의 role 선택 페이지로 리다이렉트
            response.sendRedirect("/");
            return;
        }

        // provider, providerId 추출
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        String provider = oauth2Token.getAuthorizedClientRegistrationId();

        Map<String, Object> attributes = (Map<String, Object>) oauth2Token.getPrincipal().getAttributes();

        String providerId = String.valueOf(attributes.get("providerId"));
        String email = String.valueOf(attributes.get("email"));
        String imgUrl = String.valueOf(attributes.get("imgUrl"));
        String nickname = String.valueOf(attributes.get("nickname"));

        boolean exists = authService.userExists(role, providerId, provider);
        if(exists) {
            // 로그인
            Long userId = authService.getUserId(providerId, provider, role);

            TokenResponse tokenResponse = tokenService.issueTokens(userId, role);
            cookieUtil.addHttpOnlyRefreshCookie(response, tokenResponse.getRefreshToken(), Duration.ofDays(7));

            if (devFallback()) {
                // 개발 환경에서는 성공 페이지로 응답
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(createSuccessHtmlPage(
                        "로그인 성공",
                        "OAuth 로그인이 완료되었습니다.",
                        "사용자 ID: " + userId + "<br>역할: " + role + "<br>Refresh Token이 쿠키에 저장되었습니다.",
                        tokenResponse.getAccessToken()
                ));
                return;
            }


            // 프론트로 리다이렉트
            String redirectUrl = feBaseUrl + "/auth/callback";
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.sendRedirect(redirectUrl);

            return;

        } else {
            // 신규 사용자 - OAuth 정보로 바로 회원가입 처리
            System.out.println("[OAUTH] 신규 사용자 - 바로 회원가입 처리 시작");
            System.out.println("[OAUTH] providerId: " + providerId + ", email: " + email + ", nickname: " + nickname);

            Long userId;
            AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());

            try {
                if (role == UserRole.CUSTOMER) {
                    // Customer 생성
                    Customer customer = customerService.createCustomerFromOAuth(
                            providerId, authProvider, email, imgUrl, nickname
                    );
                    userId = customer.getCustomerId();
                    System.out.println("[OAUTH] Customer 생성 완료 - ID: " + userId);
                } else if (role == UserRole.OWNER) {
                    // Owner 생성
                    Owner owner = ownerService.createOwnerFromOAuth(
                            providerId, authProvider, email, imgUrl, nickname
                    );
                    userId = owner.getOwnerId();
                    System.out.println("[OAUTH] Owner 생성 완료 - ID: " + userId);
                } else {
                    throw new IllegalStateException("Unknown role: " + role);
                }

                // JWT 토큰 발급
                TokenResponse tokenResponse = tokenService.issueTokens(userId, role);
                cookieUtil.addHttpOnlyRefreshCookie(response, tokenResponse.getRefreshToken(), Duration.ofDays(7));

                System.out.println("[OAUTH] 토큰 발급 완료 - userId: " + userId + ", role: " + role);

                if (devFallback()) {
                    // 개발 환경에서는 성공 페이지로 응답
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write(createSuccessHtmlPage(
                            "회원가입 및 로그인 성공",
                            "OAuth 회원가입과 로그인이 완료되었습니다.",
                            "사용자 ID: " + userId + "<br>역할: " + role + "<br>Refresh Token이 쿠키에 저장되었습니다.",
                            tokenResponse.getAccessToken()
                    ));
                    return;
                }

                // 프론트로 리다이렉트 (기존 사용자와 동일한 경로)
                response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                response.sendRedirect("/auth/callback");

            } catch (Exception e) {
                System.err.println("[OAUTH] 회원가입 실패: " + e.getMessage());
                e.printStackTrace();
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(createErrorHtmlPage(
                        "회원가입 실패",
                        "회원가입 처리 중 오류가 발생했습니다.",
                        "오류: " + e.getMessage()
                ));
            }
        }

    }

    private String createErrorHtmlPage(String title, String message, String action) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>%s</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }
                    .container { text-align: center; background: #f8f9fa; padding: 30px; border-radius: 10px; }
                    .error { color: #dc3545; }
                    .btn { display: inline-block; padding: 10px 20px; background: #007bff; color: white; text-decoration: none; border-radius: 5px; margin: 10px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1 class="error">❌ %s</h1>
                    <p>%s</p>
                    <p>%s</p>
                    <a href="/oauth-test" class="btn">OAuth 테스트 페이지로 이동</a>
                </div>
            </body>
            </html>
            """, title, title, message, action);
    }

    // OAuth2SuccessHandler.java의 createSuccessHtmlPage 메서드를 다음으로 교체

    private String createSuccessHtmlPage(String title, String message, String details, String accessToken) {
        return String.format("""
        <!DOCTYPE html>
        <html lang="ko">
        <head>
            <title>%s</title>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }
                
                .container {
                    background: rgba(255, 255, 255, 0.95);
                    backdrop-filter: blur(10px);
                    border-radius: 20px;
                    padding: 40px;
                    max-width: 600px;
                    width: 100%%;
                    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
                    text-align: center;
                    position: relative;
                    overflow: hidden;
                }
                
                .container::before {
                    content: '';
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    height: 4px;
                    background: linear-gradient(90deg, #4CAF50, #45a049);
                }
                
                .success-icon {
                    font-size: 4rem;
                    margin-bottom: 20px;
                    animation: bounce 1s ease-in-out;
                }
                
                @keyframes bounce {
                    0%%, 20%%, 50%%, 80%%, 100%% { transform: translateY(0); }
                    40%% { transform: translateY(-10px); }
                    60%% { transform: translateY(-5px); }
                }
                
                h1 {
                    color: #2c3e50;
                    margin-bottom: 10px;
                    font-size: 2rem;
                    font-weight: 600;
                }
                
                .subtitle {
                    color: #7f8c8d;
                    margin-bottom: 30px;
                    font-size: 1.1rem;
                }
                
                .details {
                    background: #f8f9fa;
                    padding: 20px;
                    border-radius: 10px;
                    margin: 20px 0;
                    border-left: 4px solid #4CAF50;
                }
                
                .token-section {
                    margin: 30px 0;
                }
                
                .token-label {
                    font-weight: 600;
                    color: #2c3e50;
                    margin-bottom: 10px;
                    font-size: 1.1rem;
                }
                
                .token-container {
                    position: relative;
                    background: #f1f3f4;
                    border: 2px solid #e0e0e0;
                    border-radius: 10px;
                    padding: 15px;
                    margin: 10px 0;
                }
                
                .token-display {
                    font-family: 'Courier New', monospace;
                    font-size: 12px;
                    color: #333;
                    word-break: break-all;
                    line-height: 1.4;
                    background: transparent;
                    border: none;
                    width: 100%%;
                    resize: none;
                    outline: none;
                    height: 120px;
                    overflow-y: auto;
                }
                
                .copy-btn {
                    position: absolute;
                    top: 10px;
                    right: 10px;
                    background: #4CAF50;
                    color: white;
                    border: none;
                    padding: 8px 12px;
                    border-radius: 6px;
                    cursor: pointer;
                    font-size: 12px;
                    font-weight: 500;
                    transition: all 0.2s ease;
                    display: flex;
                    align-items: center;
                    gap: 5px;
                }
                
                .copy-btn:hover {
                    background: #45a049;
                    transform: translateY(-1px);
                    box-shadow: 0 2px 8px rgba(76, 175, 80, 0.3);
                }
                
                .copy-btn.copied {
                    background: #2196F3;
                    animation: pulse 0.3s ease;
                }
                
                @keyframes pulse {
                    0%% { transform: scale(1); }
                    50%% { transform: scale(1.05); }
                    100%% { transform: scale(1); }
                }
                
                .action-buttons {
                    margin-top: 30px;
                    display: flex;
                    gap: 15px;
                    justify-content: center;
                    flex-wrap: wrap;
                }
                
                .btn {
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    padding: 12px 24px;
                    text-decoration: none;
                    border-radius: 8px;
                    font-weight: 500;
                    font-size: 14px;
                    transition: all 0.2s ease;
                    min-width: 120px;
                    justify-content: center;
                }
                
                .btn-primary {
                    background: linear-gradient(45deg, #667eea, #764ba2);
                    color: white;
                }
                
                .btn-secondary {
                    background: #6c757d;
                    color: white;
                }
                
                .btn:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                }
                
                .usage-note {
                    background: #e3f2fd;
                    border: 1px solid #2196F3;
                    border-radius: 8px;
                    padding: 15px;
                    margin-top: 20px;
                    font-size: 14px;
                    color: #1976d2;
                }
                
                .toast {
                    position: fixed;
                    top: 20px;
                    right: 20px;
                    background: #4CAF50;
                    color: white;
                    padding: 12px 20px;
                    border-radius: 8px;
                    font-weight: 500;
                    transform: translateX(100%%);
                    transition: transform 0.3s ease;
                    z-index: 1000;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                }
                
                .toast.show {
                    transform: translateX(0);
                }
                
                @media (max-width: 600px) {
                    .container {
                        margin: 10px;
                        padding: 30px 20px;
                    }
                    
                    .action-buttons {
                        flex-direction: column;
                        align-items: center;
                    }
                    
                    .btn {
                        width: 100%%;
                        max-width: 300px;
                    }
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="success-icon">🎉</div>
                <h1>카카오 로그인 성공!</h1>
                <p class="subtitle">JWT Access Token이 발급되었습니다</p>
                
                <div class="details">%s</div>
                
                <div class="token-section">
                    <div class="token-label">🔑 Access Token</div>
                    <div class="token-container">
                        <textarea class="token-display" readonly>%s</textarea>
                        <button class="copy-btn" onclick="copyToken()">
                            📋 복사
                        </button>
                    </div>
                </div>
                
                <div class="usage-note">
                    <strong>💡 사용법:</strong><br>
                    • 포스트맨에서 Authorization → Bearer Token에 위 토큰 붙여넣기<br>
                    • 또는 Headers에 <code>Authorization: Bearer &lt;토큰&gt;</code> 추가<br>
                    • 토큰 유효시간: 15분
                </div>
                
                <div class="action-buttons">
                    <a href="/auth/logout" class="btn btn-primary">
                        로그아웃
                    </a>
                </div>
            </div>
            
            <div class="toast" id="toast">토큰이 클립보드에 복사되었습니다! 📋</div>
            
            <script>
                // 토큰을 localStorage와 sessionStorage에 저장
                const token = '%s';
                localStorage.setItem('accessToken', token);
                sessionStorage.setItem('accessToken', token);
                console.log('✅ Access Token saved to storage');
                
                // 토큰 복사 함수
                function copyToken() {
                    const tokenDisplay = document.querySelector('.token-display');
                    const copyBtn = document.querySelector('.copy-btn');
                    const toast = document.getElementById('toast');
                    
                    // 토큰 선택 및 복사
                    tokenDisplay.select();
                    tokenDisplay.setSelectionRange(0, 99999); // 모바일 지원
                    
                    try {
                        document.execCommand('copy');
                        
                        // 버튼 상태 변경
                        copyBtn.textContent = '✅ 복사됨!';
                        copyBtn.classList.add('copied');
                        
                        // 토스트 메시지 표시
                        toast.classList.add('show');
                        
                        // 1.5초 후 원래 상태로 복원
                        setTimeout(() => {
                            copyBtn.textContent = '📋 복사';
                            copyBtn.classList.remove('copied');
                            toast.classList.remove('show');
                        }, 1500);
                        
                    } catch (err) {
                        console.error('복사 실패:', err);
                        copyBtn.textContent = '❌ 실패';
                        setTimeout(() => {
                            copyBtn.textContent = '📋 복사';
                        }, 1500);
                    }
                }
                
                // 현대적인 Clipboard API 지원 확인 및 사용
                if (navigator.clipboard) {
                    function copyToken() {
                        const token = '%s';
                        const copyBtn = document.querySelector('.copy-btn');
                        const toast = document.getElementById('toast');
                        
                        navigator.clipboard.writeText(token).then(() => {
                            copyBtn.textContent = '✅ 복사됨!';
                            copyBtn.classList.add('copied');
                            toast.classList.add('show');
                            
                            setTimeout(() => {
                                copyBtn.textContent = '📋 복사';
                                copyBtn.classList.remove('copied');
                                toast.classList.remove('show');
                            }, 1500);
                        }).catch(err => {
                            console.error('복사 실패:', err);
                            copyBtn.textContent = '❌ 실패';
                            setTimeout(() => {
                                copyBtn.textContent = '📋 복사';
                            }, 1500);
                        });
                    }
                }
                
                // 페이지 로드 시 애니메이션
                window.addEventListener('load', () => {
                    document.querySelector('.container').style.animation = 'fadeInUp 0.6s ease-out';
                });
                
                // fadeInUp 애니메이션 정의
                const style = document.createElement('style');
                style.textContent = `
                    @keyframes fadeInUp {
                        from {
                            opacity: 0;
                            transform: translateY(30px);
                        }
                        to {
                            opacity: 1;
                            transform: translateY(0);
                        }
                    }
                `;
                document.head.appendChild(style);
            </script>
        </body>
        </html>
        """, title, details, accessToken, accessToken, accessToken, accessToken);
    }

    private boolean devFallback() {
//        return feBaseUrl == null || feBaseUrl.isBlank();
        return false; // 배포에서는 false로 두어야 우리 프론트로 들어감
    }

}
