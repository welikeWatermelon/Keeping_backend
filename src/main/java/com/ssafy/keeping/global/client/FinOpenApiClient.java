package com.ssafy.keeping.global.client;

import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyApiHeaderDto;
import com.ssafy.keeping.domain.charge.service.SsafyFinanceApiService;
import com.ssafy.keeping.domain.user.finopenapi.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.sql.results.graph.collection.internal.MapInitializer;
import org.hibernate.sql.results.graph.collection.internal.MapInitializerProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinOpenApiClient {

    private final WebClient finOpenApiWebClient;
    private final SsafyFinanceApiService ssafyFinanceApiService;
    private final FinOpenApiProperties apiProps;

    private final Long TRANSACTION_BALANCE = 100000000L;

    @Value("${ssafy.finance.value.customer-account-type-unique-no}")
    private String customerAccountTypeUniqueNo;

    @Value("${ssafy.finance.value.owner-account-type-unique-no}")
    private String ownerAccountTypeUniqueNo;

    @Value("${ssafy.finance.value.card-unique-no}")
    private String cardUniqueNo;

    @Value("${ssafy.finance.value.category-id}")
    private String categoryId;

    public <TReq, TRes> TRes post(String path, TReq body, Class<TRes> resType) {
        log.debug("FinOpenAPI 요청 - Path: {}", path);

        return Mono.fromCallable(() -> {
                    return finOpenApiWebClient.post()
                            .uri(path)
                            .header("Content-Type", "application/json")
                            .bodyValue(body)
                            .retrieve()
                            .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                    .map(msg -> new IllegalArgumentException("finopenapi 4xx: " + msg)))
                            .onStatus(HttpStatusCode::is5xxServerError, r -> {
                                log.error("FinOpenAPI 500 에러 - Status: {}", r.statusCode());
                                return r.bodyToMono(String.class)
                                        .doOnNext(errorBody -> log.error("에러 응답: {}", errorBody))
                                        .map(msg -> new IllegalStateException("finopenapi 5xx: " + msg));
                            })
                            .bodyToMono(resType)
                            .block();
                }).subscribeOn(Schedulers.boundedElastic())
                .block();
    }


    // email 로 userKey 찾기
    public SearchUserKeyResponseDto searchUserKey(String email) {
        SearchUserKeyRequestDto requestDto = SearchUserKeyRequestDto.builder().userId(email).apiKey(apiProps.getApiKey()).build();
        return post(FinOpenApiPaths.MEMBER_SEARCH, requestDto, SearchUserKeyResponseDto.class);
    }

    // userkey 생성
    public InsertMemberResponseDto insertMember(String email) {
        InsertMemberRequestDto requestDto = InsertMemberRequestDto.builder().userId(email).apiKey(apiProps.getApiKey()).build();
        return post(FinOpenApiPaths.INSERT_MEMBER, requestDto, InsertMemberResponseDto.class);
    }

    // 계좌 생성
    public CreateAccountResponse createAccount(String userKey, String role) {
        log.debug("계좌 생성 시작");

        // TODO: 환경변수
        String apiName = "createDemandDepositAccount";

        SsafyApiHeaderDto header = ssafyFinanceApiService.createCommonHeader(userKey, apiName);

        CreateAccountRequest request = CreateAccountRequest.builder()
                .header(header)
                .accountTypeUniqueNo(role.equals("CUSTOMER") ? customerAccountTypeUniqueNo : ownerAccountTypeUniqueNo)
                .build();
        return post(FinOpenApiPaths.CREATE_ACCOUNT, request, CreateAccountResponse.class);
    }

    // card 생성
    public IssueCardResponse issueCard(String userKey, String withdrawalAccountNo) {
        String apiName = "createCreditCard";
        log.debug("카드 생성 계좌 번호 : {}", withdrawalAccountNo);

        SsafyApiHeaderDto header = ssafyFinanceApiService.createCommonHeader(userKey, apiName);

        // 출금일은 1일로 고정
        IssueCardRequest request = IssueCardRequest.builder()
                .header(header).cardUniqueNo(cardUniqueNo).withdrawalAccountNo(withdrawalAccountNo)
                .withdrawalDate("1").build();

        return post(FinOpenApiPaths.ISSUE_CARD, request, IssueCardResponse.class);
    }

    // 계좌 입금
    public AccountDepositResponse accountDeposit(String userKey, String accountNo) {
        String apiName = "updateDemandDepositAccountDeposit";

        SsafyApiHeaderDto header = ssafyFinanceApiService.createCommonHeader(userKey, apiName);

        AccountDepositRequest request = AccountDepositRequest.builder()
                .header(header).accountNo(accountNo).transactionBalance(TRANSACTION_BALANCE).build();

        return post(FinOpenApiPaths.ACCOUNT_DEPOSIT, request, AccountDepositResponse.class);
    }

    // 카테고리 조회
    public SearchCategoriesResponse searchCategories() {
        String apiName = "inquireCategoryList";

        SsafyApiHeaderDto header = ssafyFinanceApiService.createCommonHeaderWithoutUserKey(apiName);

        SearchCategoriesRequest request = SearchCategoriesRequest.create(header);

        return post(FinOpenApiPaths.SEARCH_CATEGORIES, request, SearchCategoriesResponse.class);
    }

    // 가맹점 등록
    public InsertMerchantResponse insertMerchant(String merchantName) {
        String apiName = "createMerchant";

        SsafyApiHeaderDto header = ssafyFinanceApiService.createCommonHeaderWithoutUserKey(apiName);

        InsertMerchantRequest request = InsertMerchantRequest.create(header, categoryId, merchantName);

        return post(FinOpenApiPaths.INSERT_MERCHANT, request, InsertMerchantResponse.class);
    }


}
