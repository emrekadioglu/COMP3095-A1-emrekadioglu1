INSERT INTO room (code, name, building, capacity, type, accessible, active) VALUES
('SW-305', 'Study Room 305', 'SW Building', 6, 'STUDY_ROOM', true, true),
('LAB-101', 'Computer Lab 101', 'Science Building', 30, 'LAB', true, true),
('MR-201', 'Meeting Room 201', 'Admin Building', 12, 'MEETING_ROOM', false, true),
('LH-100', 'Lecture Hall 100', 'Main Building', 150, 'LECTURE_HALL', true, true),
('SW-210', 'Study Room 210', 'SW Building', 4, 'STUDY_ROOM', false, true),
('LAB-205', 'Chemistry Lab 205', 'Science Building', 24, 'LAB', true, true);

INSERT INTO room_feature (room_id, feature) VALUES
(1, 'WHITEBOARD'),
(1, 'PROJECTOR'),
(2, 'COMPUTERS'),
(2, 'PROJECTOR'),
(3, 'VIDEO_CONFERENCE'),
(3, 'WHITEBOARD'),
(4, 'PROJECTOR'),
(4, 'VIDEO_CONFERENCE'),
(5, 'WHITEBOARD'),
(6, 'COMPUTERS');
