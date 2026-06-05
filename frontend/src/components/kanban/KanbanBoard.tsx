import { DndContext, DragOverlay, DragStartEvent, DragEndEvent, closestCorners, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { Spin, Space, Select, Input } from 'antd';
import { useState } from 'react';
import KanbanColumn from './KanbanColumn';
import TaskCard from './TaskCard';
import { useKanbanStore } from '../../stores/kanbanStore';
import UserSelect from '../common/UserSelect';
import type { Task } from '../../types';
import { TASK_PRIORITIES, TASK_TYPES } from '../../constants';

interface Props {
  projectId: number;
  loading: boolean;
  onTaskClick: (taskId: number) => void;
  filter?: { sprintId?: number };
  onTaskRemove?: (taskId: number) => void;
}

export default function KanbanBoard({ projectId, loading, onTaskClick, filter, onTaskRemove }: Props) {
  const columns = useKanbanStore((s) => s.columns);
  const columnConfigs = useKanbanStore((s) => s.columnConfigs);
  const filters = useKanbanStore((s) => s.filters);
  const moveTask = useKanbanStore((s) => s.moveTask);
  const setFilters = useKanbanStore((s) => s.setFilters);
  const loadTasks = useKanbanStore((s) => s.loadTasks);
  const [activeTask, setActiveTask] = useState<Task | null>(null);
  const [activeColumnColor, setActiveColumnColor] = useState<string | undefined>();
  const [searchText, setSearchText] = useState(filters.q ?? '');

  const pointerSensor = useSensor(PointerSensor, {
    activationConstraint: { distance: 5 },
  });
  const sensors = useSensors(pointerSensor);

  const handleDragStart = (event: DragStartEvent) => {
    const task = event.active.data.current as Task | undefined;
    if (task) {
      setActiveTask(task);
      const col = columns.find((c) => c.tasks.some((t) => t.id === task.id));
      setActiveColumnColor(col?.color);
    }
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveTask(null);
    const { active, over } = event;
    if (!over) return;

    const activeId = String(active.id);
    const overId = String(over.id);

    const activeColumn = columns.find((col) =>
      col.tasks.some((t) => String(t.id) === activeId),
    );
    if (!activeColumn) return;

    let targetColumnId: string;
    let newIndex: number;

    const targetColumn = columns.find((col) => col.id === overId);
    if (targetColumn) {
      targetColumnId = targetColumn.id;
      newIndex = targetColumn.tasks.length;
    } else {
      const overColumn = columns.find((col) =>
        col.tasks.some((t) => String(t.id) === overId),
      );
      if (!overColumn) return;
      targetColumnId = overColumn.id;
      newIndex = overColumn.tasks.findIndex((t) => String(t.id) === overId);
    }

    if (activeColumn.id === targetColumnId) {
      const col = columns.find((c) => c.id === targetColumnId)!;
      const oldIndex = col.tasks.findIndex((t) => String(t.id) === activeId);
      const tasks = [...col.tasks];
      const [removed] = tasks.splice(oldIndex, 1);
      tasks.splice(newIndex, 0, removed);

      useKanbanStore.setState((s) => ({
        columns: s.columns.map((c) =>
          c.id === targetColumnId ? { ...c, tasks } : c,
        ),
      }));
    }

    moveTask(Number(activeId), targetColumnId, newIndex);
  };

  if (loading) return <Spin style={{ display: 'block', margin: '40px auto' }} />;

  // 动态状态下拉选项
  const statusOptions = columnConfigs.map((c) => ({
    value: c.statusMapping,
    label: c.name,
  }));

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          allowClear
          placeholder="搜索任务标题或描述..."
          style={{ width: 240 }}
          value={searchText}
          onChange={(e) => {
            const val = e.target.value;
            setSearchText(val);
            if (!val) {
              setFilters({ q: undefined });
              loadTasks(projectId);
            }
          }}
          onSearch={(value) => {
            const q = value.trim() || undefined;
            setFilters({ q });
            loadTasks(projectId);
          }}
        />
        <Select
          allowClear
          placeholder="状态筛选"
          style={{ width: 130 }}
          value={filters.status}
          options={statusOptions}
          onChange={(val) => {
            setFilters({ status: val });
            loadTasks(projectId);
          }}
        />
        <Select
          allowClear
          placeholder="优先级筛选"
          style={{ width: 130 }}
          value={filters.priority}
          options={Object.values(TASK_PRIORITIES).map((p) => ({
            value: p.value,
            label: p.label,
          }))}
          onChange={(val) => {
            setFilters({ priority: val });
            loadTasks(projectId);
          }}
        />
        <Select
          allowClear
          placeholder="类型筛选"
          style={{ width: 120 }}
          value={filters.type}
          options={Object.values(TASK_TYPES).map((t) => ({
            value: t.value,
            label: t.label,
          }))}
          onChange={(val) => {
            setFilters({ type: val });
            loadTasks(projectId);
          }}
        />
        <UserSelect
          value={filters.assigneeId}
          placeholder="负责人筛选"
          style={{ width: 180 }}
          onChange={(val) => {
            setFilters({ assigneeId: val });
            loadTasks(projectId);
          }}
        />
      </Space>

      <div style={{ display: 'flex', gap: 16, overflowX: 'auto', paddingBottom: 8 }}>
        {columns.map((col) => {
          const config = columnConfigs.find((c) => c.statusMapping === col.id);
          return (
            <div
              key={col.id}
              style={{
                flex: 1,
                minWidth: 260,
                background: col.color || 'rgba(255,255,255,0.75)',
                backdropFilter: 'blur(4px)',
                WebkitBackdropFilter: 'blur(4px)',
                borderRadius: 12,
                padding: 12,
                borderTop: `3px solid ${col.color || '#3b82f6'}`,
                border: `1px solid ${col.color ? col.color + '30' : 'rgba(59,130,246,0.08)'}`,
                boxShadow: `0 4px 16px ${(col.color || '#3b82f6')}20`,
              }}
            >
              <KanbanColumn column={col} wipLimit={config?.wipLimit ?? 0}>
                <SortableContext
                  items={col.tasks.map((t) => String(t.id))}
                  strategy={verticalListSortingStrategy}
                >
                  {col.tasks.map((task) => (
                    <TaskCard key={task.id} task={task} onClick={onTaskClick} onRemove={onTaskRemove} columnColor={col.color} />
                  ))}
                </SortableContext>
              </KanbanColumn>
            </div>
          );
        })}
      </div>

      <DragOverlay>
        {activeTask ? (
          <div style={{ opacity: 0.9, transform: 'rotate(3deg)' }}>
            <TaskCard task={activeTask} columnColor={activeColumnColor} />
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
