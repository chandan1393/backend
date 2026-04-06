package com.assignease.service;

import com.assignease.dto.AppDTOs;
import com.assignease.entity.Query;
import com.assignease.entity.User;
import com.assignease.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final QueryRepository queryRepository;
    private final UserService userService;
    private final EmailService emailService;

    public AppDTOs.QueryResponse submitQuery(AppDTOs.QueryRequest request) {
        // Create user account if not exists
        User user = userService.createUserFromQuery(request.getName(), request.getEmail());

        Query query = Query.builder()
            .name(request.getName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .subject(request.getSubject())
            .message(request.getMessage())
            .status(Query.QueryStatus.PENDING)
            .user(user)
            .build();

        query = queryRepository.save(query);
        emailService.sendQueryConfirmation(request.getEmail(), request.getName(), query.getId());

        return mapToResponse(query);
    }

    public Page<AppDTOs.QueryResponse> getAllQueries(Pageable pageable) {
        return queryRepository.findAll(pageable).map(this::mapToResponse);
    }

    public AppDTOs.QueryResponse replyToQuery(Long id, String reply) {
        Query query = queryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Query not found"));

        query.setAdminReply(reply);
        query.setStatus(Query.QueryStatus.RESOLVED);
        query.setRepliedAt(LocalDateTime.now());
        query = queryRepository.save(query);

        return mapToResponse(query);
    }

    public AppDTOs.QueryResponse updateStatus(Long id, String status) {
        Query query = queryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Query not found"));
        query.setStatus(Query.QueryStatus.valueOf(status));
        return mapToResponse(queryRepository.save(query));
    }

    private AppDTOs.QueryResponse mapToResponse(Query q) {
        AppDTOs.QueryResponse r = new AppDTOs.QueryResponse();
        r.setId(q.getId());
        r.setName(q.getName());
        r.setEmail(q.getEmail());
        r.setSubject(q.getSubject());
        r.setMessage(q.getMessage());
        r.setStatus(q.getStatus().name());
        r.setAdminReply(q.getAdminReply());
        r.setCreatedAt(q.getCreatedAt());
        return r;
    }
}
