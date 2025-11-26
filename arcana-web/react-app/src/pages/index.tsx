import { GetServerSideProps } from 'next';
import Head from 'next/head';
import Link from 'next/link';
import { Layout } from '@/components/Layout';
import { Card } from '@/components/Card';
import { Activity, Users, Puzzle, Server } from 'lucide-react';

interface DashboardProps {
  stats: {
    activeUsers: number;
    totalPlugins: number;
    systemHealth: string;
    uptime: string;
  };
}

/**
 * Dashboard Home Page
 *
 * Server-side rendered dashboard with platform statistics.
 */
export default function Dashboard({ stats }: DashboardProps) {
  return (
    <>
      <Head>
        <title>Arcana Cloud - Dashboard</title>
      </Head>

      <Layout>
        <div className="space-y-6">
          <div className="flex justify-between items-center">
            <h1>Dashboard</h1>
            <span className="text-sm text-gray-500">Welcome back!</span>
          </div>

          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card>
              <div className="flex items-center space-x-4">
                <div className="p-3 bg-primary-100 rounded-lg">
                  <Users className="w-6 h-6 text-primary-600" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">Active Users</p>
                  <p className="text-2xl font-bold">{stats.activeUsers}</p>
                </div>
              </div>
            </Card>

            <Card>
              <div className="flex items-center space-x-4">
                <div className="p-3 bg-arcana-success/10 rounded-lg">
                  <Puzzle className="w-6 h-6 text-arcana-success" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">Active Plugins</p>
                  <p className="text-2xl font-bold">{stats.totalPlugins}</p>
                </div>
              </div>
            </Card>

            <Card>
              <div className="flex items-center space-x-4">
                <div className="p-3 bg-arcana-accent/10 rounded-lg">
                  <Activity className="w-6 h-6 text-arcana-accent" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">System Health</p>
                  <p className="text-2xl font-bold text-arcana-success">
                    {stats.systemHealth}
                  </p>
                </div>
              </div>
            </Card>

            <Card>
              <div className="flex items-center space-x-4">
                <div className="p-3 bg-gray-100 dark:bg-gray-700 rounded-lg">
                  <Server className="w-6 h-6 text-gray-600 dark:text-gray-300" />
                </div>
                <div>
                  <p className="text-sm text-gray-500">Uptime</p>
                  <p className="text-2xl font-bold">{stats.uptime}</p>
                </div>
              </div>
            </Card>
          </div>

          {/* Quick Actions */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card>
              <h3 className="mb-4">Quick Actions</h3>
              <div className="space-y-2">
                <Link
                  href="/plugins"
                  className="block p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span>Manage Plugins</span>
                    <span className="text-primary-600">→</span>
                  </div>
                </Link>
                <Link
                  href="/users"
                  className="block p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span>User Management</span>
                    <span className="text-primary-600">→</span>
                  </div>
                </Link>
                <Link
                  href="/audit"
                  className="block p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span>View Audit Logs</span>
                    <span className="text-primary-600">→</span>
                  </div>
                </Link>
              </div>
            </Card>

            <Card>
              <h3 className="mb-4">Recent Activity</h3>
              <div className="space-y-3">
                <div className="flex items-center space-x-3 text-sm">
                  <div className="w-2 h-2 bg-arcana-success rounded-full"></div>
                  <span className="text-gray-500">Plugin installed</span>
                  <span className="font-medium">Audit Log Plugin</span>
                </div>
                <div className="flex items-center space-x-3 text-sm">
                  <div className="w-2 h-2 bg-primary-500 rounded-full"></div>
                  <span className="text-gray-500">User login</span>
                  <span className="font-medium">admin@arcana.cloud</span>
                </div>
                <div className="flex items-center space-x-3 text-sm">
                  <div className="w-2 h-2 bg-arcana-warning rounded-full"></div>
                  <span className="text-gray-500">Config updated</span>
                  <span className="font-medium">SSR settings</span>
                </div>
              </div>
            </Card>
          </div>
        </div>
      </Layout>
    </>
  );
}

export const getServerSideProps: GetServerSideProps<DashboardProps> = async (
  context
) => {
  // In production, fetch from API
  // const res = await fetch(`${process.env.API_BASE_URL}/api/v1/dashboard/stats`);
  // const stats = await res.json();

  // Mock data for demonstration
  const stats = {
    activeUsers: 42,
    totalPlugins: 5,
    systemHealth: 'Healthy',
    uptime: '99.9%',
  };

  return {
    props: {
      stats,
    },
  };
};
