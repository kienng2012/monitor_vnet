package vn.vnet.dto;

import lombok.*;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor

public class MonitorVNetDto {
    @NotNull(message = "{NotNull}")
    @NotEmpty(message = "{NotEmpty}")
    private String username;
    @NotNull(message = "{NotNull}")
    @NotEmpty(message = "{NotEmpty}")
    private String password;
    @NotNull(message = "{NotNull}")
    @NotEmpty(message = "{NotEmpty}")
    private String msisdn;
    @NotNull(message = "{NotNull}")
    @NotEmpty(message = "{NotEmpty}")
    private String brandname;
    @NotNull(message = "{NotNull}")
    private Long requestTime;
    @NotNull(message = "{NotNull}")

    private Long messageId;
    @NotNull(message = "{NotNull}")
    @NotEmpty(message = "{NotEmpty}")
    private String message;

    @Override
    public String toString() {
        return "MonitorVNetDto{" +
                "msisdn='" + msisdn + '\'' +
                ", brandname='" + brandname + '\'' +
                ", requestTime=" + requestTime +
                ", messageId=" + messageId +
                ", message='" + message + '\'' +
                '}';
    }
}
