package com.mastercard.paymenttransfersystem.domain.account.controller;

import com.mastercard.paymenttransfersystem.config.error.ExceptionMapper;
import com.mastercard.paymenttransfersystem.domain.account.controller.dto.AccountBalanceDTO;
import com.mastercard.paymenttransfersystem.domain.account.controller.dto.AccountDTO;
import com.mastercard.paymenttransfersystem.domain.account.controller.dto.AccountStatementItemDTO;
import com.mastercard.paymenttransfersystem.domain.account.controller.dto.TransferRequestDTO;
import com.mastercard.paymenttransfersystem.domain.account.model.Account;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountState;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountStatementItemType;
import com.mastercard.paymenttransfersystem.domain.account.repository.AccountRepository;
import com.mastercard.paymenttransfersystem.domain.transaction.model.Transaction;
import com.mastercard.paymenttransfersystem.domain.transaction.repository.TransactionRepository;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountControllerTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;
    private String baseUrl = "http://localhost:";

    @BeforeEach
    public void setUp() {
        baseUrl += port + "/api/v1/accounts";
        accountRepository.deleteAll();
    }

    @Test
    public void Get_account_balance() {
        // Given
        Account account = givenAccount();

        // When
        String url = baseUrl + "/" + account.getId() + "/balance";
        AccountBalanceDTO response = restTemplate.getForObject(url, AccountBalanceDTO.class);

        // Then
        assertNotNull(response);
        assertNotNull(response.getBalance());
        assertNotNull(response.getCurrency());
        assertEquals(account.getId(), response.getAccountId());
        assertEquals(account.getBalance(), response.getBalance());
        assertEquals(account.getCurrency(), response.getCurrency());

    }

    @Test
    public void Get_account_balance_non_existing_account() {
        // Given no account

        // When
        String url = baseUrl + "/777777/balance";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.GET, null, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, exchange.getStatusCode());
    }

    @Test
    public void Transfer_money() {
        // Given
        Account sender = givenAccount();
        Account recipient = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(5000);
        TransferRequestDTO dto = new TransferRequestDTO(recipient.getId(), transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String url = baseUrl + "/" + sender.getId() + "/transfer";
        ResponseEntity<AccountBalanceDTO> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, AccountBalanceDTO.class);

        // Then
        assertNotNull(exchange.getBody());
        assertEquals(sender.getBalance().subtract(transferAmount), exchange.getBody().getBalance());

        Account account = accountRepository.findById(recipient.getId()).get();
        assertEquals(account.getBalance(), recipient.getBalance().add(transferAmount));

        Transaction transaction = transactionRepository.findLatestTransactions(sender.getId(), 1L).get(0);
        assertNotNull(transaction);
        assertEquals(transaction.getSenderAccountId(), sender.getId());
        assertEquals(transaction.getRecipientAccountId(), recipient.getId());
        assertEquals(transaction.getAmount(), transferAmount);
        assertNotNull(transaction.getCreatedAt());
        assertNotNull(transaction.getModifiedAt());
    }

    @Test
    public void Transfer_money_to_non_existing_account() {
        // Given
        Account sender = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(5000);
        TransferRequestDTO dto = new TransferRequestDTO(77777L, transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String url = baseUrl + "/" + sender.getId() + "/transfer";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, exchange.getStatusCode());
    }

    @Test
    public void Transfer_money_from_non_existing_account() {
        // Given
        Account recipient = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(5000);
        TransferRequestDTO dto = new TransferRequestDTO(recipient.getId(), transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String url = baseUrl + "/7777777/transfer";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, exchange.getStatusCode());
    }

    @Test
    public void Transfer_money_with_insufficient_funds_fails() {
        // Given
        Account sender = givenAccountWithNoBalance();
        Account recipient = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(5000);
        TransferRequestDTO dto = new TransferRequestDTO(recipient.getId(), transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String url = baseUrl + "/" + sender.getId() + "/transfer";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getStatusCode());
    }

    @Test
    public void Transfer_negative_amount_fails() {
        // Given
        Account sender = givenAccount();
        Account recipient = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(-10000);
        TransferRequestDTO dto = new TransferRequestDTO(recipient.getId(), transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String url = baseUrl + "/" + sender.getId() + "/transfer";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getStatusCode());
    }

    @Test
    public void Transfer_to_self_fails() {
        // Given
        Account sender = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(10000);
        TransferRequestDTO dto = new TransferRequestDTO(sender.getId(), transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String url = baseUrl + "/" + sender.getId() + "/transfer";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getStatusCode());
    }

    @Test
    public void Get_mini_statement() {
        // Given
        Account account1 = givenAccount();
        Account account2 = givenAccount();
        List<Transaction> transactions = givenTransactions(account1.getId(), account2.getId());

        // When
        String url = baseUrl + "/" + account1.getId() + "/statements/mini";
        AccountStatementItemDTO[] response = restTemplate.getForObject(url, AccountStatementItemDTO[].class);

        // Then
        assertNotNull(response);
        assertEquals(transactions.size(), response.length);

        assertEquals(account2.getId(), response[0].getAccountId());
        assertEquals(account2.getId(), response[1].getAccountId());
        assertEquals(account2.getId(), response[2].getAccountId());

        assertEquals(transactions.get(0).getAmount(), response[0].getAmount());
        assertEquals(transactions.get(1).getAmount(), response[1].getAmount());
        assertEquals(transactions.get(2).getAmount(), response[2].getAmount());

        assertEquals(transactions.get(0).getCurrency(), response[0].getCurrency());
        assertEquals(transactions.get(1).getCurrency(), response[1].getCurrency());
        assertEquals(transactions.get(2).getCurrency(), response[2].getCurrency());

        assertEquals(AccountStatementItemType.DEBIT.name(), response[0].getType());
        assertEquals(AccountStatementItemType.DEBIT.name(), response[1].getType());
        assertEquals(AccountStatementItemType.CREDIT.name(), response[2].getType());
    }

    @Test
    public void Get_account() {
        // Given
        Account account = givenAccount();

        // When
        String url = baseUrl + "/" + account.getId();
        AccountDTO response = restTemplate.getForObject(url, AccountDTO.class);

        // Then
        assertNotNull(response);
        assertEquals(account.getId(), response.getId());
        assertEquals(account.getBalance(), response.getBalance());
        assertEquals(account.getCurrency(), response.getCurrency());
    }

    @Test
    public void Get_non_existing_account_fails() {
        // Given no account

        // When
        String url = baseUrl + "/777777";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.GET, null, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, exchange.getStatusCode());
    }

    @Test
    public void Get_accounts() {
        // Given
        Account account1 = givenAccount();
        Account account2 = givenAccount();
        List<Account> all = accountRepository.findAll();

        // When
        AccountDTO[] response = restTemplate.getForObject(baseUrl, AccountDTO[].class);

        // Then
        assertNotNull(response);
        assertEquals(account1.getId(), response[0].getId());
        assertEquals(account1.getBalance(), response[0].getBalance());
        assertEquals(account1.getCurrency(), response[0].getCurrency());

        assertEquals(account2.getId(), response[1].getId());
        assertEquals(account2.getBalance(), response[1].getBalance());
        assertEquals(account2.getCurrency(), response[1].getCurrency());
    }

    @Test
    public void Update_account() {
        // Given
        Account account = givenAccount();

        // When
        AccountDTO dto = AccountDTO.builder()
                .id(account.getId())
                .balance(account.getBalance())
                .state(AccountState.INACTIVE.name())
                .currency(account.getCurrency())
                .build();

        HttpEntity<AccountDTO> entity = new HttpEntity<>(dto);
        String url = baseUrl + "/" + account.getId();
        ResponseEntity<AccountDTO> response = restTemplate.exchange(url, HttpMethod.PUT, entity, AccountDTO.class);

        // Then
        assertNotNull(response);
        assertNotEquals(account.getState().name(), response.getBody().getState());
        assertEquals(AccountState.INACTIVE.name(), response.getBody().getState());
    }

    @Test
    public void Update_non_existing_account_fails() {
        // Given no account

        // When

        HttpEntity<AccountDTO> entity = new HttpEntity<>(AccountDTO.builder()
                .state("ACTIVE")
                .build());
        String url = baseUrl + "/777777";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, exchange.getStatusCode());
    }

    @Test
    public void Delete_account() {
        // Given
        Account account = givenAccount();

        // When
        String url = baseUrl + "/" + account.getId();
        ResponseEntity<AccountDTO> response = restTemplate.exchange(url, HttpMethod.DELETE, null, AccountDTO.class);

        // Then
        assertNotNull(response);
        assertNotEquals(account.getState().name(), response.getBody().getState());
        assertEquals(AccountState.SET_FOR_DELETION.name(), response.getBody().getState());
    }

    @Test
    public void Delete_non_existing_account_fails() {
        // Given no account

        // When
        String url = baseUrl + "/777777";
        ResponseEntity<ExceptionMapper.ErrorResponse> exchange = restTemplate.exchange(url, HttpMethod.DELETE, null, ExceptionMapper.ErrorResponse.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, exchange.getStatusCode());
    }

    @Test
    public void Create_account() {
        // Given
        AccountDTO dto = AccountDTO.builder()
                .balance(BigDecimal.TEN)
                .currency("USD")
                .state(AccountState.ACTIVE.name())
                .build();

        // When
        AccountDTO response = restTemplate.postForObject(baseUrl, dto, AccountDTO.class);

        // Then
        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(dto.getBalance(), response.getBalance());
        assertEquals(dto.getCurrency(), response.getCurrency());
        assertEquals(dto.getState(), response.getState());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getModifiedAt());

    }

    @Test
    @SneakyThrows
    void Transfer_twice_to_same_account_concurrently() {
        // Given
        Account sender = givenAccount();
        Account recipient = givenAccount();
        BigDecimal transferAmount = BigDecimal.valueOf(500);
        TransferRequestDTO dto = new TransferRequestDTO(recipient.getId(), transferAmount, "USD");
        HttpEntity<TransferRequestDTO> entity = new HttpEntity<>(dto);

        // When
        String fullUrl = baseUrl + "/" + sender.getId() + "/transfer";
        URI url = new URI(fullUrl);

        Thread thread1 = new Thread(new PostRequestTask<>(url, entity));
        Thread thread2 = new Thread(new PostRequestTask<>(url, entity));
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then
        BigDecimal senderUpdatedBalance = accountRepository.findById(sender.getId()).get().getBalance();
        BigDecimal recipientUpdatedBalance = accountRepository.findById(recipient.getId()).get().getBalance();

        assertEquals(sender.getBalance().subtract(transferAmount).subtract(transferAmount), senderUpdatedBalance);
        assertEquals(sender.getBalance().add(transferAmount).add(transferAmount), recipientUpdatedBalance);
    }

    @Test
    @SneakyThrows
    @Disabled
    void Transfer_twice_cross_accounts_concurrently() {
        // Given
        Account account1 = givenAccount();
        Account account2 = givenAccount();
        BigDecimal transferAmount1 = BigDecimal.valueOf(700);
        BigDecimal transferAmount2 = BigDecimal.valueOf(500);
        TransferRequestDTO dto1 = new TransferRequestDTO(account1.getId(), transferAmount1, "USD");
        TransferRequestDTO dto2 = new TransferRequestDTO(account2.getId(), transferAmount2, "USD");
        HttpEntity<TransferRequestDTO> entity1 = new HttpEntity<>(dto1);
        HttpEntity<TransferRequestDTO> entity2 = new HttpEntity<>(dto2);

        // When
        String fullUrl1 = baseUrl + "/" + account2.getId() + "/transfer";
        String fullUrl2 = baseUrl + "/" + account1.getId() + "/transfer";
        URI url1 = new URI(fullUrl1);
        URI url2 = new URI(fullUrl2);

        Thread thread1 = new Thread(new PostRequestTask<>(url1, entity1));
        Thread thread2 = new Thread(new PostRequestTask<>(url2, entity2));
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then
        BigDecimal account1UpdatedBalance = accountRepository.findById(account1.getId()).get().getBalance();
        BigDecimal account2UpdatedBalance = accountRepository.findById(account2.getId()).get().getBalance();

        assertEquals(account1.getBalance().subtract(transferAmount2).add(transferAmount1), account1UpdatedBalance);
        assertEquals(account2.getBalance().add(transferAmount2).subtract(transferAmount1), account2UpdatedBalance);
    }

    private Account givenAccount() {
        Account account = Account.builder()
                .balance(BigDecimal.valueOf(10000))
                .currency("USD")
                .state(AccountState.ACTIVE)
                .build();
        return accountRepository.save(account);
    }

    private Account givenAccountWithNoBalance() {
        Account account = Account.builder()
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .state(AccountState.ACTIVE)
                .build();
        return accountRepository.save(account);
    }

    private List<Transaction> givenTransactions(Long senderId, Long recipientId) {
        Transaction t1 = Transaction.builder()
                .senderAccountId(senderId)
                .recipientAccountId(recipientId)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .build();

        Transaction t2 = Transaction.builder()
                .senderAccountId(senderId)
                .recipientAccountId(recipientId)
                .amount(BigDecimal.valueOf(8510))
                .currency("USD")
                .build();

        Transaction t3 = Transaction.builder()
                .senderAccountId(recipientId)
                .recipientAccountId(senderId)
                .amount(BigDecimal.valueOf(5555))
                .currency("USD")
                .build();
        return transactionRepository.saveAll(List.of(t1, t2, t3));
    }

    public class PostRequestTask<T> implements Runnable {
        private final URI url;
        private final HttpEntity<T> request;
        private final TestRestTemplate restTemplate = new TestRestTemplate();

        public PostRequestTask(URI url, HttpEntity<T> request) {
            this.url = url;
            this.request = request;
        }

        @SneakyThrows
        @Override
        public void run() {
            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
        }
    }
}
