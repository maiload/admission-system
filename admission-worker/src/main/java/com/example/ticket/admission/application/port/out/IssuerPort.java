package com.example.ticket.admission.application.port.out;

import com.example.ticket.admission.application.dto.IssueResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Lua-backed atomic admission issuer.
 * Pops queue members, creates enter tokens, updates qstate.
 */
public interface IssuerPort {

    /**
     * Issue admission tokens atomically via Lua.
     *
     * @param eventId       event ID
     * @param scheduleId    schedule ID
     * @param maxIssue      max tokens to issue this call
     * @param rateCap       per-second rate cap
     * @param concurrencyCap simultaneous active cap
     * @param enterTtlSec   enter token TTL
     * @param qstateTtlSec  qstate TTL (to refresh on update)
     * @param rateTtlSec    rate counter TTL
     * @param tokenPairs    list of [jti, enterToken] pairs (flat: jti1, token1, jti2, token2, ...)
     * @return issue result
     */
    Mono<IssueResult> issue(String eventId, String scheduleId,
                            int maxIssue, int rateCap, int concurrencyCap,
                            int enterTtlSec, int qstateTtlSec, int rateTtlSec,
                            List<String> tokenPairs);
}
