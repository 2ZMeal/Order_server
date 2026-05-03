package com.ezmeal.order.domain.exception;

import com.ezmeal.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND,         "ORDER_404",   "주문을 찾을 수 없습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND,         "STORE_404",   "가게를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND,       "PRODUCT_404", "상품을 찾을 수 없습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST,  "ORDER_400_1", "이미 취소된 주문입니다."),
    ORDER_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN,     "ORDER_403_1", "본인의 주문만 취소할 수 있습니다."),
    ORDER_CANCEL_TIME_EXPIRED(HttpStatus.BAD_REQUEST,"ORDER_400_2", "주문 후 5분이 경과하여 취소가 불가합니다."),
    ORDER_CANCEL_DELIVERING(HttpStatus.BAD_REQUEST,  "ORDER_400_3", "배달 중인 주문은 취소할 수 없습니다."),
    ORDER_CANCEL_COMPLETED(HttpStatus.BAD_REQUEST,   "ORDER_400_4", "이미 완료된 주문은 취소할 수 없습니다."),
    ORDER_STATUS_CHANGE_FORBIDDEN(HttpStatus.FORBIDDEN,   "ORDER_403_2", "본인 가게의 주문만 상태를 변경할 수 있습니다."),
    ORDER_STATUS_INVALID_TRANSITION(HttpStatus.BAD_REQUEST,"ORDER_400_5", "허용되지 않는 주문 상태 변경입니다."),
    ORDER_STATUS_ALREADY_FINAL(HttpStatus.BAD_REQUEST,    "ORDER_400_6", "완료되었거나 취소된 주문은 상태를 변경할 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN,     "ORDER_403_3", "해당 주문에 대한 접근 권한이 없습니다."),
    STORE_OWNER_NOT_FOUND(HttpStatus.NOT_FOUND,   "STORE_404_1", "운영 중인 가게 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
