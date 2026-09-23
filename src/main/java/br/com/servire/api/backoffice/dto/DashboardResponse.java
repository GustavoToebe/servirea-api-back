package br.com.servire.api.backoffice.dto;

public record DashboardResponse(long total, long ativas, long trial, long bloqueadas, long canceladas) {
}
