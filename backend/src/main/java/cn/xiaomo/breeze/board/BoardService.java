package cn.xiaomo.breeze.board;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final KanbanBoardMapper boardMapper;
    private final KanbanColumnMapper columnMapper;
    private final TaskMapper taskMapper;

    /** status_mapping 受保护的列 —— 不可修改 status_mapping，不可删除 */
    private static final Set<String> PROTECTED_STATUSES = Set.of("todo", "in_progress", "done");

    // ——— 默认列定义 ———

    private static final Object[][] DEFAULT_COLUMNS = {
        { "待规划", "backlog",     "#94a3b8", 0, 0 },
        { "待处理", "todo",        "#e2e8f0", 0, 1 },
        { "进行中", "in_progress", "#bfdbfe", 10, 2 },
        { "评审中", "review",      "#fef08a", 5, 3 },
        { "测试中", "testing",     "#fce7f3", 5, 4 },
        { "已完成", "done",        "#bbf7d0", 0, 5 },
    };

    // ——— 获取看板 ———

    public BoardDTO getBoard(Long projectId) {
        KanbanBoard board = getBoardInternal(projectId);
        return toBoardDTO(board);
    }

    /** 获取 KanbanBoard 实体（内部使用） */
    public KanbanBoard getBoardInternal(Long projectId) {
        KanbanBoard board = boardMapper.selectOne(new LambdaQueryWrapper<KanbanBoard>()
                .eq(KanbanBoard::getProjectId, projectId)
                .eq(KanbanBoard::getIsDefault, true));
        if (board == null) {
            board = initDefaultBoard(projectId);
        }
        return board;
    }

    // ——— 初始化默认看板 ———

    @Transactional(rollbackFor = Exception.class)
    public KanbanBoard initDefaultBoard(Long projectId) {
        KanbanBoard board = new KanbanBoard();
        board.setProjectId(projectId);
        board.setName("默认看板");
        board.setIsDefault(true);
        boardMapper.insert(board);

        for (Object[] col : DEFAULT_COLUMNS) {
            KanbanColumn column = new KanbanColumn();
            column.setBoardId(board.getId());
            column.setName((String) col[0]);
            column.setStatusMapping((String) col[1]);
            column.setColor((String) col[2]);
            column.setWipLimit((Integer) col[3]);
            column.setSortOrder((Integer) col[4]);
            columnMapper.insert(column);
        }
        return board;
    }

    // ——— 列 CRUD ———

    @Transactional(rollbackFor = Exception.class)
    public ColumnDTO addColumn(Long boardId, ColumnCreateRequest req) {
        // 校验 status_mapping 不重复
        if (columnMapper.exists(new LambdaQueryWrapper<KanbanColumn>()
                .eq(KanbanColumn::getBoardId, boardId)
                .eq(KanbanColumn::getStatusMapping, req.getStatusMapping()))) {
            throw new IllegalArgumentException("状态映射 " + req.getStatusMapping() + " 已被其他列占用");
        }

        KanbanColumn column = new KanbanColumn();
        column.setBoardId(boardId);
        column.setName(req.getName());
        column.setStatusMapping(req.getStatusMapping());
        column.setWipLimit(req.getWipLimit() != null ? req.getWipLimit() : 0);
        column.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        column.setColor(req.getColor() != null ? req.getColor() : "#808080");
        columnMapper.insert(column);
        return toColumnDTO(column);
    }

    @Transactional(rollbackFor = Exception.class)
    public ColumnDTO updateColumn(Long columnId, ColumnUpdateRequest req) {
        KanbanColumn column = columnMapper.selectById(columnId);
        if (column == null) {
            throw new IllegalArgumentException("列不存在");
        }

        // 保护逻辑：todo/in_progress/done 不可修改 status_mapping
        if (req.getStatusMapping() != null && !req.getStatusMapping().equals(column.getStatusMapping())) {
            if (PROTECTED_STATUSES.contains(column.getStatusMapping())) {
                throw new IllegalArgumentException("核心列 " + column.getStatusMapping() + " 的状态映射不可修改");
            }
            // 修改 status_mapping 时同步更新该列下所有任务的 status
            List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .eq(Task::getKanbanColumnId, columnId));
            for (Task task : tasks) {
                task.setStatus(req.getStatusMapping());
                taskMapper.updateById(task);
            }
            column.setStatusMapping(req.getStatusMapping());
        }

        if (req.getName() != null) column.setName(req.getName());
        if (req.getWipLimit() != null) column.setWipLimit(req.getWipLimit());
        if (req.getSortOrder() != null) column.setSortOrder(req.getSortOrder());
        if (req.getColor() != null) column.setColor(req.getColor());

        columnMapper.updateById(column);
        return toColumnDTO(column);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteColumn(Long columnId, Long migrateToColumnId) {
        KanbanColumn column = columnMapper.selectById(columnId);
        if (column == null) {
            throw new IllegalArgumentException("列不存在");
        }
        if (PROTECTED_STATUSES.contains(column.getStatusMapping())) {
            throw new IllegalArgumentException("核心列 " + column.getStatusMapping() + " 不可删除");
        }
        if (migrateToColumnId == null) {
            throw new IllegalArgumentException("请指定任务迁移目标列");
        }
        KanbanColumn targetColumn = columnMapper.selectById(migrateToColumnId);
        if (targetColumn == null) {
            throw new IllegalArgumentException("目标列不存在");
        }

        // 迁移任务
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getKanbanColumnId, columnId));
        for (Task task : tasks) {
            task.setKanbanColumnId(migrateToColumnId);
            task.setStatus(targetColumn.getStatusMapping());
            taskMapper.updateById(task);
        }

        columnMapper.deleteById(columnId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSortOrder(List<ColumnSortDTO> sorts) {
        for (ColumnSortDTO sort : sorts) {
            KanbanColumn column = columnMapper.selectById(sort.getId());
            if (column != null) {
                column.setSortOrder(sort.getSortOrder());
                columnMapper.updateById(column);
            }
        }
    }

    /** 获取项目中所有有效状态值（从列配置读取），供 TaskService / AI Tools 使用 */
    public List<String> getValidStatuses(Long projectId) {
        BoardDTO board = getBoard(projectId);
        return board.getColumns().stream()
                .map(ColumnDTO::getStatusMapping)
                .toList();
    }

    /** 判断 status_mapping 是否为受保护的核心列 */
    public static boolean isProtectedStatus(String statusMapping) {
        return PROTECTED_STATUSES.contains(statusMapping);
    }

    // ——— DTO 转换 ———

    private BoardDTO toBoardDTO(KanbanBoard board) {
        BoardDTO dto = new BoardDTO();
        dto.setId(board.getId());
        dto.setProjectId(board.getProjectId());
        dto.setName(board.getName());
        dto.setIsDefault(board.getIsDefault());

        List<KanbanColumn> columns = columnMapper.selectList(
                new LambdaQueryWrapper<KanbanColumn>()
                        .eq(KanbanColumn::getBoardId, board.getId())
                        .orderByAsc(KanbanColumn::getSortOrder));
        dto.setColumns(columns.stream().map(this::toColumnDTO).toList());
        return dto;
    }

    private ColumnDTO toColumnDTO(KanbanColumn column) {
        ColumnDTO dto = new ColumnDTO();
        dto.setId(column.getId());
        dto.setName(column.getName());
        dto.setStatusMapping(column.getStatusMapping());
        dto.setWipLimit(column.getWipLimit());
        dto.setSortOrder(column.getSortOrder());
        dto.setColor(column.getColor());
        return dto;
    }
}
