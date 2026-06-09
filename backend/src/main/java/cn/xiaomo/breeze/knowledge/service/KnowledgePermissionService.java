package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocumentPermission;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeDocumentPermissionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文档权限服务 */
@Service
@RequiredArgsConstructor
public class KnowledgePermissionService {

    private final KnowledgeDocumentPermissionMapper permissionMapper;

    /**
     * 继承父文件夹权限或设置默认权限。
     * 子项默认继承父文件夹权限，可单独覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public void inheritOrSetDefault(Long documentId, Long parentFolderId,
                                    String defaultPermission, Long userId) {
        // 尝试从父文件夹继承
        if (parentFolderId != null) {
            List<KnowledgeDocumentPermission> parentPerms =
                    permissionMapper.selectByDocumentId(parentFolderId);
            if (!parentPerms.isEmpty()) {
                for (KnowledgeDocumentPermission pp : parentPerms) {
                    KnowledgeDocumentPermission perm = new KnowledgeDocumentPermission();
                    perm.setDocumentId(documentId);
                    perm.setUserId(pp.getUserId());
                    perm.setPermission(pp.getPermission());
                    perm.setGrantedBy(userId);
                    permissionMapper.insert(perm);
                }
                // 确保创建者至少有 manage
                ensureCreatorManage(documentId, userId);
                return;
            }
        }

        // 无父文件夹或父文件夹无权限 → 设置默认值
        if ("only_me".equals(defaultPermission)) {
            // 仅自己可见
            grant(documentId, userId, "manage", userId);
        } else {
            // 所有人可读（everyone）
            grant(documentId, userId, "manage", userId);
            // 所有人可读 = 不添加额外限制，检索时默认返回
        }
    }

    /** 授予权限 */
    public void grant(Long documentId, Long userId, String permission, Long grantedBy) {
        KnowledgeDocumentPermission existing =
                permissionMapper.selectByDocAndUser(documentId, userId);
        if (existing != null) {
            existing.setPermission(permission);
            permissionMapper.updateById(existing);
        } else {
            KnowledgeDocumentPermission perm = new KnowledgeDocumentPermission();
            perm.setDocumentId(documentId);
            perm.setUserId(userId);
            perm.setPermission(permission);
            perm.setGrantedBy(grantedBy);
            permissionMapper.insert(perm);
        }
    }

    /** 检查用户是否有 manage 权限 */
    public void checkManagePermission(Long documentId, Long userId) {
        KnowledgeDocumentPermission perm =
                permissionMapper.selectByDocAndUser(documentId, userId);
        if (perm == null || !"manage".equals(perm.getPermission())) {
            throw new RuntimeException("无操作权限");
        }
    }

    /** 获取用户对文档的权限级别 */
    public String getPermission(Long documentId, Long userId) {
        KnowledgeDocumentPermission perm =
                permissionMapper.selectByDocAndUser(documentId, userId);
        return perm != null ? perm.getPermission() : null;
    }

    /** 文档是否对用户可见 */
    public boolean isVisible(Long documentId, Long userId) {
        // 如果文档没有设置任何限制性权限 → 所有人可见
        List<KnowledgeDocumentPermission> perms =
                permissionMapper.selectByDocumentId(documentId);
        if (perms.isEmpty()) return true;
        // 有权限设置 → 检查用户是否在列表中
        return perms.stream().anyMatch(p -> p.getUserId().equals(userId));
    }

    /** 获取文档所有权限 */
    public List<KnowledgeDocumentPermission> getPermissions(Long documentId) {
        return permissionMapper.selectByDocumentId(documentId);
    }

    private void ensureCreatorManage(Long documentId, Long userId) {
        KnowledgeDocumentPermission p =
                permissionMapper.selectByDocAndUser(documentId, userId);
        if (p == null) {
            grant(documentId, userId, "manage", userId);
        } else if (!"manage".equals(p.getPermission())) {
            p.setPermission("manage");
            permissionMapper.updateById(p);
        }
    }
}
