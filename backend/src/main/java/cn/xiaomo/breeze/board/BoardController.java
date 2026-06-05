package cn.xiaomo.breeze.board;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /** 获取项目默认看板（含列） */
    @GetMapping
    public ResponseEntity<BoardDTO> getBoard(@PathVariable Long projectId) {
        return ResponseEntity.ok(boardService.getBoard(projectId));
    }

    /** 新增列 */
    @PostMapping("/columns")
    public ResponseEntity<ColumnDTO> addColumn(@PathVariable Long projectId,
                                               @Valid @RequestBody ColumnCreateRequest req) {
        KanbanBoard board = boardService.getBoardInternal(projectId);
        return ResponseEntity.ok(boardService.addColumn(board.getId(), req));
    }

    /** 更新列属性 */
    @PatchMapping("/columns/{columnId}")
    public ResponseEntity<ColumnDTO> updateColumn(@PathVariable Long projectId,
                                                  @PathVariable Long columnId,
                                                  @Valid @RequestBody ColumnUpdateRequest req) {
        return ResponseEntity.ok(boardService.updateColumn(columnId, req));
    }

    /** 删除列（需指定任务迁移目标列） */
    @DeleteMapping("/columns/{columnId}")
    public ResponseEntity<Void> deleteColumn(@PathVariable Long projectId,
                                             @PathVariable Long columnId,
                                             @RequestParam Long migrateToColumnId) {
        boardService.deleteColumn(columnId, migrateToColumnId);
        return ResponseEntity.noContent().build();
    }

    /** 批量更新列排序 */
    @PutMapping("/columns")
    public ResponseEntity<Void> updateSortOrder(@PathVariable Long projectId,
                                                @RequestBody List<ColumnSortDTO> sorts) {
        boardService.updateSortOrder(sorts);
        return ResponseEntity.noContent().build();
    }

    /** 获取项目所有有效状态值 */
    @GetMapping("/statuses")
    public ResponseEntity<List<String>> getValidStatuses(@PathVariable Long projectId) {
        return ResponseEntity.ok(boardService.getValidStatuses(projectId));
    }
}
