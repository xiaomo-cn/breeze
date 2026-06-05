package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 职务管理业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;

    /** 获取所有职务，按 sort_order 排序 */
    public List<Position> listAll() {
        return positionMapper.selectList(
            new LambdaQueryWrapper<Position>().orderByAsc(Position::getSortOrder));
    }

    /** 创建新职务，名称不能重复 */
    @Transactional(rollbackFor = Exception.class)
    public Position create(String name, String color) {
        if (positionMapper.exists(new LambdaQueryWrapper<Position>()
                .eq(Position::getName, name))) {
            throw new IllegalArgumentException("职务名称已存在: " + name);
        }

        // 计算 sort_order：放到末尾
        Integer maxSort = positionMapper.selectList(
                new LambdaQueryWrapper<Position>()
                    .orderByDesc(Position::getSortOrder)
                    .last("LIMIT 1"))
            .stream().findFirst().map(Position::getSortOrder).orElse(0);

        Position p = new Position();
        p.setName(name);
        p.setColor(color != null ? color : "#1677ff");
        p.setSortOrder(maxSort + 1);
        positionMapper.insert(p);
        return p;
    }

    /** 更新职务名称或颜色 */
    @Transactional(rollbackFor = Exception.class)
    public Position update(Long id, String name, String color) {
        Position p = positionMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("职务不存在: " + id);
        }
        if (name != null && !name.isBlank()) {
            // 检查名称是否被其他职务占用
            if (positionMapper.exists(new LambdaQueryWrapper<Position>()
                    .eq(Position::getName, name)
                    .ne(Position::getId, id))) {
                throw new IllegalArgumentException("职务名称已存在: " + name);
            }
            p.setName(name);
        }
        if (color != null) {
            p.setColor(color);
        }
        positionMapper.updateById(p);
        return p;
    }

    /** 删除职务（users.position_id 通过 ON DELETE SET NULL 自动置空） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Position p = positionMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("职务不存在: " + id);
        }
        positionMapper.deleteById(id);
    }
}
