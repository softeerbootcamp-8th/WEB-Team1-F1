package com.softeer.race.notification.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.notification.application.NotificationService;
import com.softeer.race.notification.presentation.response.NotificationSliceResponse;
import com.softeer.race.notification.presentation.response.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ResponseEntity<NotificationSliceResponse> list(
            @LoginUser AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cursor) {

        NotificationSliceResponse response = NotificationSliceResponse.from(
                notificationService.list(authenticatedUser.id(), cursor));

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> countUnread(
            @LoginUser AuthenticatedUser authenticatedUser) {

        long unreadCount = notificationService.countUnread(authenticatedUser.id());

        return ResponseEntity.ok(new UnreadCountResponse(unreadCount));
    }

    // 본문이 없다. 바꿀 값이 "읽음" 하나로 정해져 있어 클라이언트가 보낼 것이 없다
    @Override
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long notificationId) {

        notificationService.markRead(authenticatedUser.id(), notificationId);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@LoginUser AuthenticatedUser authenticatedUser) {
        notificationService.markAllRead(authenticatedUser.id());

        return ResponseEntity.noContent().build();
    }
}