package zalord.message_service.dto.response;

import java.util.List;

public record PageResponse<T>(
		List<T> items,
		int page,
		int size,
		long total,
		int totalPages
	) {
		public List<T> getContent() { return items(); }
		public int getPage() { return page(); }
		public int getSize() { return size(); }
		public long getTotal() { return total(); }
		public int getTotalPages() { return totalPages(); }
	}
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size > 0 ? (int) ((total + size - 1) / size) : 0;
        return new PageResponse<>(items, page, size, total, totalPages);
    }
}
