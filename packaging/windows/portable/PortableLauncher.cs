// Portable single-file starter for the Lumora launcher (C# 5, .NET Framework 4.8 - preinstalled on Windows 10/11).
//
// LumoraClient.exe carries the complete jpackage app image (launcher + Java runtime) as an embedded zip.
// First start: verify the zip's SHA-256, extract it to %LOCALAPPDATA%\LumoraClient\portable\<hash>,
// then run LumoraLauncher.exe. Later starts run the extracted launcher directly.
// Built by `gradlew :launcher-app:portableExe` with the csc.exe that ships with Windows.
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("Lumora Client")]
[assembly: AssemblyProduct("Lumora Client")]
[assembly: AssemblyDescription("Lumora Client launcher (portable)")]

namespace Lumora.Portable
{
    internal static class Program
    {
        private const string Title = "Lumora Client";
        private const string Marker = ".lumora-extracted";

        [STAThread]
        private static int Main(string[] args)
        {
            try
            {
                string expected = ReadResourceText("version.txt").Trim().ToLowerInvariant();
                string baseDir = Environment.GetEnvironmentVariable("LUMORA_PORTABLE_DIR");
                if (string.IsNullOrEmpty(baseDir))
                {
                    baseDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                        "LumoraClient", "portable");
                }
                string target = Path.Combine(baseDir, expected.Substring(0, 16));
                string exe = Path.Combine(target, "LumoraLauncher", "LumoraLauncher.exe");

                if (!File.Exists(Path.Combine(target, Marker)) || !File.Exists(exe))
                {
                    if (!ExtractWithProgress(baseDir, target, expected))
                    {
                        return 1;
                    }
                    CleanupOldVersions(baseDir, target);
                }
                RecordStarterPath(baseDir);
                return Launch(exe, args);
            }
            catch (Exception e)
            {
                MessageBox.Show("Der Lumora Launcher konnte nicht gestartet werden:\n\n" + e.Message,
                    Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
        }

        /// Where this starter lives, for a launcher started directly from the extracted folder (pinned icon): it then
        /// still knows which EXE to update (LauncherUpdate.STARTER_RECORD). Failure is harmless.
        private static void RecordStarterPath(string baseDir)
        {
            try
            {
                string file = Path.Combine(baseDir, "starter-path.txt");
                string me = Application.ExecutablePath;
                if (!File.Exists(file) || File.ReadAllText(file, Encoding.UTF8).Trim() != me)
                {
                    File.WriteAllText(file, me, new UTF8Encoding(false));
                }
            }
            catch { }
        }

        private static int Launch(string exe, string[] args)
        {
            StringBuilder sb = new StringBuilder();
            foreach (string a in args)
            {
                if (sb.Length > 0) sb.Append(' ');
                sb.Append('"').Append(a.Replace("\"", "\\\"")).Append('"');
            }
            ProcessStartInfo psi = new ProcessStartInfo(exe, sb.ToString());
            psi.UseShellExecute = false;
            psi.WorkingDirectory = Path.GetDirectoryName(exe);
            // the launcher replaces this file on a self-update (it is not locked: this starter exits right away)
            psi.EnvironmentVariables["LUMORA_PORTABLE_EXE"] = Application.ExecutablePath;
            Process.Start(psi);
            return 0;
        }

        private static bool ExtractWithProgress(string baseDir, string target, string expectedSha256)
        {
            Exception failure = null;
            Form form = new Form();
            form.Text = Title;
            form.FormBorderStyle = FormBorderStyle.FixedDialog;
            form.MaximizeBox = false;
            form.MinimizeBox = false;
            form.StartPosition = FormStartPosition.CenterScreen;
            form.ClientSize = new Size(420, 96);
            try { form.Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location); } catch { }
            Label label = new Label();
            label.Text = "Lumora Client wird beim ersten Start vorbereitet ...";
            label.SetBounds(16, 16, 388, 20);
            ProgressBar bar = new ProgressBar();
            bar.SetBounds(16, 48, 388, 22);
            bar.Maximum = 1000;
            form.Controls.Add(label);
            form.Controls.Add(bar);

            Thread worker = new Thread(delegate ()
            {
                try
                {
                    Extract(baseDir, target, expectedSha256, delegate (double f)
                    {
                        form.BeginInvoke((MethodInvoker)delegate { bar.Value = (int)Math.Min(1000, f * 1000); });
                    });
                }
                catch (Exception e)
                {
                    failure = e;
                }
                form.BeginInvoke((MethodInvoker)delegate { form.Close(); });
            });
            worker.IsBackground = true;
            form.Shown += delegate { worker.Start(); };
            Application.EnableVisualStyles();
            Application.Run(form);
            worker.Join();
            if (failure != null)
            {
                MessageBox.Show("Lumora Client konnte nicht entpackt werden:\n\n" + failure.Message
                    + "\n\nZiel: " + target, Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
                return false;
            }
            return true;
        }

        private static void Extract(string baseDir, string target, string expectedSha256, Action<double> progress)
        {
            Directory.CreateDirectory(baseDir);
            string tmp = target + ".tmp-" + Process.GetCurrentProcess().Id;
            if (Directory.Exists(tmp)) Directory.Delete(tmp, true);
            string zipPath = Path.Combine(baseDir, "payload-" + Process.GetCurrentProcess().Id + ".zip");
            try
            {
                // 1. copy the embedded zip to disk and verify it against the hash recorded at build time
                using (Stream res = Assembly.GetExecutingAssembly().GetManifestResourceStream("app.zip"))
                using (FileStream fs = File.Create(zipPath))
                using (SHA256 sha = SHA256.Create())
                {
                    if (res == null) throw new IOException("Eingebettetes Paket fehlt.");
                    byte[] buf = new byte[1 << 16];
                    long done = 0;
                    int n;
                    while ((n = res.Read(buf, 0, buf.Length)) > 0)
                    {
                        fs.Write(buf, 0, n);
                        sha.TransformBlock(buf, 0, n, null, 0);
                        done += n;
                        progress(0.3 * done / Math.Max(1, res.Length));
                    }
                    sha.TransformFinalBlock(new byte[0], 0, 0);
                    string actual = BitConverter.ToString(sha.Hash).Replace("-", "").ToLowerInvariant();
                    if (actual != expectedSha256)
                    {
                        throw new IOException("Prüfsumme des eingebetteten Pakets stimmt nicht (Datei beschädigt?).");
                    }
                }

                // 2. extract, refusing entries that would escape the target folder
                string root = Path.GetFullPath(tmp) + Path.DirectorySeparatorChar;
                using (ZipArchive zip = ZipFile.OpenRead(zipPath))
                {
                    int i = 0;
                    foreach (ZipArchiveEntry entry in zip.Entries)
                    {
                        string dest = Path.GetFullPath(Path.Combine(tmp, entry.FullName));
                        if (!dest.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                            throw new IOException("Ungültiger Pfad im Paket: " + entry.FullName);
                        if (entry.FullName.EndsWith("/"))
                        {
                            Directory.CreateDirectory(dest);
                        }
                        else
                        {
                            Directory.CreateDirectory(Path.GetDirectoryName(dest));
                            entry.ExtractToFile(dest, true);
                        }
                        i++;
                        progress(0.3 + 0.7 * i / Math.Max(1, zip.Entries.Count));
                    }
                }
                File.WriteAllText(Path.Combine(tmp, Marker), expectedSha256);
                if (File.Exists(Path.Combine(target, Marker)))
                {
                    return; // a second instance finished first; its copy is identical (same hash)
                }
                if (Directory.Exists(target)) Directory.Delete(target, true); // incomplete earlier attempt
                Directory.Move(tmp, target);
            }
            finally
            {
                try { if (File.Exists(zipPath)) File.Delete(zipPath); } catch { }
                try { if (Directory.Exists(tmp)) Directory.Delete(tmp, true); } catch { }
            }
        }

        /// Removes older extracted versions. Only folders this starter created are touched: name of exactly
        /// 16 hex characters AND containing the marker file. A version that is still running stays (files in use).
        private static void CleanupOldVersions(string baseDir, string keep)
        {
            foreach (string dir in Directory.GetDirectories(baseDir))
            {
                if (string.Equals(Path.GetFullPath(dir), Path.GetFullPath(keep), StringComparison.OrdinalIgnoreCase)) continue;
                if (!IsHex16(Path.GetFileName(dir)) || !File.Exists(Path.Combine(dir, Marker))) continue;
                try { Directory.Delete(dir, true); } catch { }
            }
        }

        private static bool IsHex16(string name)
        {
            if (name == null || name.Length != 16) return false;
            foreach (char c in name)
            {
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
            }
            return true;
        }

        private static string ReadResourceText(string name)
        {
            using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
            {
                if (s == null) throw new IOException("Ressource fehlt: " + name);
                using (StreamReader r = new StreamReader(s, Encoding.ASCII)) return r.ReadToEnd();
            }
        }
    }
}
