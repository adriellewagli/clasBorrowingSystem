package model;

import java.time.LocalDateTime;

public class PasswordTicket {
    private int ticketId;
    private int userId;
    private String username;
    private String fullName;
    private LocalDateTime requestDate;
    private String status;

    public PasswordTicket(int ticketId, int userId, String username, String fullName, LocalDateTime requestDate, String status) {
        this.ticketId = ticketId;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.requestDate = requestDate;
        this.status = status;
    }

    public int getTicketId() { return ticketId; }
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public LocalDateTime getRequestDate() { return requestDate; }
    public String getStatus() { return status; }
}