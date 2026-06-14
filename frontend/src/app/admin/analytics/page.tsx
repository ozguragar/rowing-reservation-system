'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { Analytics } from '@/types';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';

export default function AnalyticsPage() {
  const [data, setData] = useState<Analytics[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/admin/analytics/occupancy')
      .then(res => setData(res.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const chartData = data.map(d => ({
    name: `${d.date.substring(5)} ${d.sessionTime.substring(0, 5)}`,
    occupancy: d.occupancyPercentage,
    booked: d.totalBooked,
    capacity: d.totalCapacity,
  }));

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Analytics Dashboard</h1>

        {loading ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="card">
                <p className="text-sm text-gray-500 dark:text-gray-400">Total Sessions (7 days)</p>
                <p className="text-3xl font-bold text-gray-900 dark:text-gray-100">{data.length}</p>
              </div>
              <div className="card">
                <p className="text-sm text-gray-500 dark:text-gray-400">Avg Occupancy</p>
                <p className="text-3xl font-bold text-primary-600 dark:text-primary-400">
                  {data.length > 0 ? (data.reduce((a, b) => a + b.occupancyPercentage, 0) / data.length).toFixed(1) : 0}%
                </p>
              </div>
              <div className="card">
                <p className="text-sm text-gray-500 dark:text-gray-400">Total Bookings</p>
                <p className="text-3xl font-bold text-accent-600 dark:text-green-400">{data.reduce((a, b) => a + b.totalBooked, 0)}</p>
              </div>
            </div>

            <div className="card">
              <h2 className="text-lg font-semibold mb-4">Boat Occupancy — Last 7 Days</h2>
              <div className="h-96">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="name" tick={{ fontSize: 10 }} angle={-45} textAnchor="end" height={80} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} label={{ value: 'Occupancy %', angle: -90, position: 'insideLeft' }} />
                    <Tooltip formatter={(value: number) => [`${value}%`, 'Occupancy']} />
                    <Bar dataKey="occupancy" radius={[4, 4, 0, 0]}>
                      {chartData.map((entry, i) => (
                        <Cell key={i} fill={entry.occupancy > 80 ? '#22c55e' : entry.occupancy > 50 ? '#3b82f6' : '#f59e0b'} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="card overflow-hidden p-0">
              <table className="w-full">
                <thead>
                  <tr className="bg-gray-50 dark:bg-gray-900 border-b">
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Date</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Time</th>
                    <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Booked</th>
                    <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Capacity</th>
                    <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Occupancy</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {data.map(d => (
                    <tr key={d.sessionId} className="hover:bg-gray-50 hover:dark:bg-gray-900">
                      <td className="px-4 py-3 text-sm">{d.date}</td>
                      <td className="px-4 py-3 text-sm">{d.sessionTime}</td>
                      <td className="px-4 py-3 text-sm text-right">{d.totalBooked}</td>
                      <td className="px-4 py-3 text-sm text-right">{d.totalCapacity}</td>
                      <td className="px-4 py-3 text-sm text-right font-medium">{d.occupancyPercentage}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
