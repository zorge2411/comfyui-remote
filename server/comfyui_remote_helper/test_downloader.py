import asyncio
import unittest

from downloader import QUEUE_EVENT, Downloader
from validation import RequestError


class QueueTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        self.events = []
        self.ran = []
        self.release = {}
        self.dl = Downloader(lambda event, data: self.events.append((event, data)))

        async def fake_download(job):
            self.ran.append(job["filename"])
            self.dl._set_status(job, "downloading")
            gate = self.release.setdefault(job["filename"], asyncio.Event())
            await gate.wait()
            if job["cancel"]:
                return self.dl._finish(job, "cancelled")
            if job["filename"].startswith("bad"):
                return self.dl._fail(job, "Download failed: HTTP 404")
            self.dl._finish(job, "done")

        self.dl._download = fake_download

    def add(self, name):
        return self.dl.enqueue("https://huggingface.co/x/" + name, "vae", name, "/models/vae/" + name)

    def gate(self, name):
        return self.release.setdefault(name, asyncio.Event())

    async def settle(self):
        for _ in range(5):
            await asyncio.sleep(0)

    def statuses(self):
        return [(j["filename"], j["status"], j["position"]) for j in self.dl.jobs()]

    async def asyncTearDown(self):
        if self.dl._worker:
            self.dl._worker.cancel()

    async def test_runs_one_at_a_time_in_order(self):
        for name in ("a", "b", "c"):
            self.add(name)
        await self.settle()
        self.assertEqual(["a"], self.ran)
        self.assertEqual([("a", "downloading", None), ("b", "queued", 1), ("c", "queued", 2)], self.statuses())
        self.gate("a").set()
        await self.settle()
        self.assertEqual(["a", "b"], self.ran)

    async def test_move_changes_what_runs_next(self):
        for name in ("a", "b", "c"):
            self.add(name)
        await self.settle()
        c = next(j for j in self.dl.jobs() if j["filename"] == "c")
        self.dl.move(c["id"], 1)
        self.assertEqual(QUEUE_EVENT, self.events[-1][0])
        self.assertEqual([("a", "downloading", None), ("c", "queued", 1), ("b", "queued", 2)], self.statuses())
        self.gate("a").set()
        await self.settle()
        self.assertEqual(["a", "c"], self.ran)

    async def test_move_clamps_and_ignores_non_queued(self):
        a = self.add("a")
        b = self.add("b")
        c = self.add("c")
        await self.settle()
        self.assertIsNone(self.dl.move(a["id"], 1))  # downloading
        self.dl.move(b["id"], 99)
        self.assertEqual(["c", "b"], [j["filename"] for j in self.dl.jobs() if j["status"] == "queued"])
        self.dl.move(b["id"], -5)
        self.assertEqual(["b", "c"], [j["filename"] for j in self.dl.jobs() if j["status"] == "queued"])

    async def test_cancel_queued_removes_it_from_the_order(self):
        self.add("a")
        b = self.add("b")
        self.add("c")
        await self.settle()
        self.dl.cancel(b["id"])
        self.assertEqual([("a", "downloading", None), ("c", "queued", 1), ("b", "cancelled", None)], self.statuses())
        self.gate("a").set()
        await self.settle()
        self.assertEqual(["a", "c"], self.ran)

    async def test_retry_requeues_at_the_end(self):
        bad = self.add("bad.safetensors")
        self.gate("bad.safetensors").set()
        await self.settle()
        self.add("b")
        await self.settle()
        again = self.dl.retry(bad["id"], lambda path: False)
        self.assertNotEqual(bad["id"], again["id"])
        self.assertEqual([("b", "downloading", None), ("bad.safetensors", "queued", 1)], self.statuses())

    async def test_retry_refuses_existing_file_and_active_jobs(self):
        bad = self.add("bad.safetensors")
        self.gate("bad.safetensors").set()
        await self.settle()
        with self.assertRaises(RequestError) as e:
            self.dl.retry(bad["id"], lambda path: True)
        self.assertEqual(409, e.exception.status)
        running = self.add("b")
        await self.settle()
        with self.assertRaises(RequestError):
            self.dl.retry(running["id"], lambda path: False)
        self.assertIsNone(self.dl.retry("nope", lambda path: False))

    async def test_clear_finished_keeps_active_and_queued(self):
        self.add("a")
        self.gate("a").set()
        await self.settle()
        self.add("b")
        self.add("c")
        await self.settle()
        self.dl.clear_finished()
        self.assertEqual([("b", "downloading", None), ("c", "queued", 1)], self.statuses())


if __name__ == "__main__":
    unittest.main()
