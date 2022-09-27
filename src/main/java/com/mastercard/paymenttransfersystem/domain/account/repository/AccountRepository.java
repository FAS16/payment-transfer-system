package com.mastercard.paymenttransfersystem.domain.account.repository;

import com.mastercard.paymenttransfersystem.domain.account.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.math.BigDecimal;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query(value ="UPDATE account SET balance = balance + :amount WHERE id = :accountId", nativeQuery = true)
    @Modifying
    @Transactional
    void updateAccountBalance(@Param("amount") BigDecimal amount, @Param("accountId") Long accountId);

    @Query(value = "SELECT balance FROM Account WHERE id = :accountId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    BigDecimal getAccountBalance(@Param("accountId") Long accountId);
}