# MiniDB

## Concurrent Relational Database Engine

MiniDB is a lightweight relational database engine built from scratch in
Java for studying the internal mechanisms of Database Management Systems
and Operating Systems.

Instead of relying on MySQL, PostgreSQL, SQLite, or another database
engine, MiniDB implements its own storage, memory management, query
execution, transactions, concurrency control, and recovery mechanisms.

The project is designed as a B.Tech 5th-semester combined DBMS and
Operating Systems project, with the architecture intentionally structured
so it can be extended into a deeper systems project.

---

## Project Goals

MiniDB aims to demonstrate how a database engine interacts with
operating-system concepts underneath a SQL interface.

The primary goals are:

- Build a relational database engine from scratch.
- Implement persistent storage using files and fixed-size pages.
- Implement a database buffer pool for memory management.
- Parse and execute a useful subset of SQL.
- Support transactions and rollback.
- Support concurrent transactions.
- Implement database locking and deadlock detection.
- Implement B+ tree indexing.
- Implement write-ahead logging.
- Implement crash recovery.
- Measure and document the performance of the implemented components.

---

## Example

The intended user experience is:

```text
MiniDB> CREATE DATABASE college;
Database created.

MiniDB> USE college;
Using database: college

MiniDB> CREATE TABLE students (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    age INT
);
Table created.

MiniDB> INSERT INTO students VALUES (1, 'Rahul', 20);
1 row inserted.

MiniDB> SELECT * FROM students WHERE age > 18;

+----+-------+-----+
| id | name  | age |
+----+-------+-----+
| 1  | Rahul | 20  |
+----+-------+-----+

1 row returned.