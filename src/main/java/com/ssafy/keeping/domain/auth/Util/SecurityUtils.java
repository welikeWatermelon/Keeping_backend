package com.ssafy.keeping.domain.auth.Util;

import com.ssafy.keeping.domain.auth.enums.UserRole;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class SecurityUtils {

    // 현재 인증된 userId 반환
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    // 현재 인증된 사용자 권한 반환
    public static UserRole getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Collection<? extends GrantedAuthority> grantedAuthorities = authentication.getAuthorities();

        for(GrantedAuthority grantedAuthority : grantedAuthorities) {
            String role = grantedAuthority.getAuthority();
            if(role.startsWith("ROLE_")) {
                String value = role.substring(5);

                try {
                    return UserRole.valueOf(value);
                } catch (IllegalArgumentException e) {
                    // CUSTOMER, OWNER 외
                    throw new CustomException(ErrorCode.INVALID_ROLE);
                }
            }
        }
        throw new CustomException(ErrorCode.ROLE_NOT_FOUND);
    }

    // 현재 사용자가 특정 권한을 가지고 있는지 확인
    public static boolean hasRole(UserRole userRole) {
        try {
            UserRole currentUserRole = getCurrentUserRole();
            return currentUserRole == userRole;
        } catch (CustomException e) {
            return false;
        }
    }

    // 현재 사용자가 OWNER 인지
    public static boolean isOwner() {
        return hasRole(UserRole.OWNER);
    }

    // 현재 사용자가 CUSTOMER 인지
    public static boolean isCustomer() {
        return hasRole(UserRole.CUSTOMER);
    }

    // 인증된 사용자인지
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null &&
                authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal());
    }

    // 본인 확인
    public static boolean isCurrentUser(Long userId) {
        try {
            Long currentUserId = getCurrentUserId();
            return currentUserId.equals(userId);
        } catch (CustomException e) {
            return false;
        }
    }

    // OWNER 권한 검증
    public void validateOwnerAccess() {
        if(!isOwner()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    // CUSTOMER 권한 검증
    public void validateCustomerAccess() {
        if(!isCustomer()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    // 본인 확인 검증
    public void validateCurrentUser(Long userId) {
        if(!isCurrentUser(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    // 인증 검증
    public void validateAuthenticated() {
        if(!isAuthenticated()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
