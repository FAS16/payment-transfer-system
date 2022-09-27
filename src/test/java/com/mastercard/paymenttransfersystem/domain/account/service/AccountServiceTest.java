package com.mastercard.paymenttransfersystem.domain.account.service;

import com.mastercard.paymenttransfersystem.domain.account.model.Account;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountState;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountStatementItem;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.AccountNotFoundException;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.InsufficientFundsException;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.InvalidAmountException;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.SelfTransferException;
import com.mastercard.paymenttransfersystem.domain.account.repository.AccountRepository;
import com.mastercard.paymenttransfersystem.domain.transaction.model.Transaction;
import com.mastercard.paymenttransfersystem.domain.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionService transactionService;
    private AccountService accountService;


    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transactionService);
    }

    @Test
    void Get_existing_account_by_id_returns_account() {

        // Given
        Long accountId = 345L;
        Account account = Account.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(5000))
                .currency("USD")
                .state(AccountState.ACTIVE)
                .build();
        given(accountRepository.findById(accountId)).willReturn(Optional.ofNullable(account));

        // When
        Account retrievedAccount = accountService.getAccountById(accountId);

        // Then
        verify(accountRepository).findById(accountId);
        assertEquals(account, retrievedAccount);
    }

    @Test
    void Get_non_existing_account_by_id_throws_AccountNotFoundException() {

        // Given
        Long accountId = 345L;
        given(accountRepository.findById(accountId)).willReturn(Optional.empty());

        // When Then
        assertThrows(AccountNotFoundException.class, () -> accountService.getAccountById(accountId));
        verify(accountRepository).findById(accountId);
    }

    @Test
    void Get_mini_statement_returns_successfully() {

        // Given
        Long accountId = 345L;
        ReflectionTestUtils.setField(accountService, "sizeOfMiniStatement", 20L);
        Transaction transaction = new Transaction(1L, accountId, 433L, BigDecimal.TEN, "USD", ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.existsById(accountId)).willReturn(true);
        given(transactionService.getLatestTransactions(accountId, 20L)).willReturn(List.of(transaction));

        // When
        List<AccountStatementItem> miniStatement = accountService.getMiniStatement(accountId);

        // Then
        verify(accountRepository).existsById(accountId);
        verify(transactionService).getLatestTransactions(accountId, 20L);
        assertEquals(List.of(transaction).size(), miniStatement.size());
    }

    @Test
    void Get_mini_statement_for_non_existing_account_throws_AccountNotFoundException() {

        // Given
        Long accountId = 345L;
        given(accountRepository.existsById(accountId)).willReturn(false);

        // When Then
        assertThrows(AccountNotFoundException.class, () -> accountService.getMiniStatement(accountId));
        verify(accountRepository).existsById(accountId);
    }

    @Test
    @Disabled
    void Transfer_money_adds_and_deducts_successfully() {

        // Given
        Long senderAccountId = 3332L;
        Long recipientAccountId = 9992L;
        BigDecimal amount = BigDecimal.TEN;
        Account sender = new Account(senderAccountId, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        Account recipient = new Account(recipientAccountId, BigDecimal.valueOf(500), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        Transaction transaction = Transaction.builder()
                .recipientAccountId(recipientAccountId)
                .senderAccountId(senderAccountId)
                .amount(amount)
                .currency("USD")
                .build();

        given(accountRepository.findById(senderAccountId))
                .willReturn(Optional.of(sender));
        given(accountRepository.findById(recipientAccountId))
                .willReturn(Optional.of(recipient));
        given(transactionService.saveTransaction(transaction)).willReturn(transaction);

        // When
        accountService.transferMoney(senderAccountId, recipientAccountId, amount);

        // Then
        verify(accountRepository).findById(senderAccountId);
        verify(accountRepository).findById(recipientAccountId);
        verify(transactionService).saveTransaction(transaction);
        assertEquals(BigDecimal.valueOf(590), sender.getBalance());
        assertEquals(BigDecimal.valueOf(510), recipient.getBalance());

    }


    @Test
    @Disabled
    void Transfer_money_insufficient_funds_throws_InsufficientFundsException() {
        // Given
        Long senderAccountId = 3332L;
        Long recipientAccountId = 9992L;
        BigDecimal amount = BigDecimal.TEN;
        Account sender = new Account(senderAccountId, BigDecimal.valueOf(0), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        Account recipient = new Account(recipientAccountId, BigDecimal.valueOf(500), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(senderAccountId))
                .willReturn(Optional.of(sender));
        given(accountRepository.findById(recipientAccountId))
                .willReturn(Optional.of(recipient));

        // When Then
        assertThrows(InsufficientFundsException.class, () -> accountService.transferMoney(senderAccountId, recipientAccountId, amount));
        verify(accountRepository).findById(senderAccountId);
        verify(accountRepository).findById(recipientAccountId);

    }


    @Test
    @Disabled
    void Transfer_money_negative_amounts_throws_InvalidAmountException() {
        // Given
        Long senderAccountId = 3332L;
        Long recipientAccountId = 9992L;
        BigDecimal amount = BigDecimal.valueOf(-10);
        Account sender = new Account(senderAccountId, BigDecimal.valueOf(100), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        Account recipient = new Account(recipientAccountId, BigDecimal.valueOf(500), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(senderAccountId))
                .willReturn(Optional.of(sender));
        given(accountRepository.findById(recipientAccountId))
                .willReturn(Optional.of(recipient));

        // When Then
        assertThrows(InvalidAmountException.class, () -> accountService.transferMoney(senderAccountId, recipientAccountId, amount));
        verify(accountRepository).findById(senderAccountId);
        verify(accountRepository).findById(recipientAccountId);
    }

    @Test
    @Disabled
    void Transfer_money_to_self_throws_SelfTransferException() {
        // Given
        Long accountId = 3332L;
        BigDecimal amount = BigDecimal.valueOf(10);
        Account account = new Account(accountId, BigDecimal.valueOf(100), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(accountId))
                .willReturn(Optional.of(account));

        // When Then
        assertThrows(SelfTransferException.class, () -> accountService.transferMoney(accountId, accountId, amount));
        verify(accountRepository, (times(2))).findById(accountId);
    }

    @Test
    void Get_all_accounts_successfully() {
        // Given
        Account account1 = new Account(111L, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        Account account2 = new Account(222L, BigDecimal.valueOf(500), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        List<Account> accounts = List.of(account1, account2);
        given(accountRepository.findAll()).willReturn(accounts);

        // When
        List<Account> actualAccounts = accountService.getAll();

        // Then
        verify(accountRepository).findAll();
        assertNotNull(actualAccounts);
        assertEquals(actualAccounts, accounts);
    }

    @Test
    void Create_account_successfully() {
        // Given
        Account account = new Account(111L, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.save(account)).willReturn(account);

        // When
        Account actualAccount = accountService.createAccount(account);

        // Then
        verify(accountRepository).save(account);
        assertNotNull(actualAccount);
        assertEquals(account, actualAccount);
    }

    @Test
    void Update_account_successfully() {
        // Given
        Account account = new Account(111L, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(account.getId())).willReturn(Optional.of(account));
        given(accountRepository.save(account)).willReturn(account);

        // When
        Account actualAccount = accountService.updateAccount(account.getId(), account);

        // Then
        verify(accountRepository).findById(account.getId());
        verify(accountRepository).save(account);
        assertNotNull(actualAccount);
        assertEquals(account, actualAccount);
    }

    @Test
    void Update_account_non_existing_account_throws_AccountNotFoundException() {
        // Given
        Account account = new Account(111L, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(account.getId())).willReturn(Optional.empty());

        // When Then
        assertThrows(AccountNotFoundException.class, () -> accountService.updateAccount(account.getId(), account));
        verify(accountRepository).findById(account.getId());
    }

    @Test
    void Delete_account_successfully() {
        // Given
        Account account = new Account(111L, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(account.getId())).willReturn(Optional.of(account));
        account.setState(AccountState.SET_FOR_DELETION);
        given(accountRepository.save(account)).willReturn(account);

        // When
        Account actualAccount = accountService.deleteAccount(account.getId());

        // Then
        verify(accountRepository).findById(account.getId());
        verify(accountRepository).save(account);
        assertNotNull(actualAccount);
        assertEquals(AccountState.SET_FOR_DELETION, actualAccount.getState());
    }

    @Test
    void Delete_account_non_existing_throws_AccountNotFoundException() {
        // Given
        Account account = new Account(111L, BigDecimal.valueOf(600), "USD", AccountState.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now());
        given(accountRepository.findById(account.getId())).willReturn(Optional.empty());

        // When Then
        assertThrows(AccountNotFoundException.class, () -> accountService.deleteAccount(account.getId()));
        verify(accountRepository).findById(account.getId());
    }
}
