import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Segmented, Spin } from 'antd';
import { getGanttData } from '../api/gantt';
import { listSprints } from '../api/sprints';
import type { GanttData, Sprint } from '../types';
import Gantt from 'frappe-gantt';
import '../styles/frappe-gantt.css';

export default function GanttPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const ganttRef = useRef<HTMLDivElement>(null);
  const ganttInstance = useRef<any>(null);
  const [data, setData] = useState<GanttData | null>(null);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [viewMode, setViewMode] = useState<string>('Day');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getGanttData(projectId).then(setData).finally(() => setLoading(false));
    listSprints(projectId).then(setSprints).catch(() => {});
  }, [projectId]);

  useEffect(() => {
    if (!data || !ganttRef.current) return;

    if (ganttInstance.current) {
      ganttInstance.current = null as any;
      ganttRef.current.innerHTML = '';
    }

    const tasks = data.tasks.map(t => ({
      id: String(t.id),
      name: `${t.key} ${t.title}`,
      start: t.startDate || new Date().toISOString().split('T')[0],
      end: t.endDate || new Date().toISOString().split('T')[0],
      progress: t.status === 'done' ? 100 : t.status === 'in_progress' ? 50 : 0,
      dependencies: t.dependencies.map(String),
    }));

    ganttInstance.current = new Gantt(ganttRef.current, tasks, {
      view_mode: viewMode as any,
      date_format: 'YYYY-MM-DD',
      bar_height: 30,
      bar_corner_radius: 3,
      arrow_curve: 5,
      on_date_change: (task: any, start: Date, end: Date) => {
        console.log('Date changed:', task.id, start, end);
      },
    });
  }, [data, viewMode]);

  return (
    <div style={{ padding: 24 }}>
      <div style={{
        background: 'rgba(255,255,255,0.8)',
        borderRadius: 12,
        padding: 20,
        boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
        border: '1px solid rgba(59,130,246,0.06)',
      }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Segmented
          value={viewMode}
          onChange={setViewMode}
          options={[
            { label: '季度', value: 'Quarter Day' },
            { label: '半天', value: 'Half Day' },
            { label: '天', value: 'Day' },
            { label: '周', value: 'Week' },
            { label: '月', value: 'Month' },
          ]}
        />
      </div>
        <Spin spinning={loading}>
          <div ref={ganttRef} style={{ overflow: 'auto' }} />
        </Spin>
      </div>
    </div>
  );
}
