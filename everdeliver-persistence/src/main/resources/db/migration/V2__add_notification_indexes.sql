CREATE INDEX idx_notifications_channel_created_at ON notifications (channel, created_at DESC);
CREATE INDEX idx_notifications_updated_at ON notifications (updated_at DESC);
