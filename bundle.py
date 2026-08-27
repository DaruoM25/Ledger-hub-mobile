import os

# Configuration des filtres spécifiques à ton projet
EXCLUDE_DIRS = {'.git', 'node_modules', '.terraform', 'dist', '__pycache__', 'tests-e2e'}
EXCLUDE_FILES = {'package-lock.json', 'yarn.lock', '.DS_Store', 'bundle.py', 'ledgerhub-mobile-contxt.md'}
VALID_EXTENSIONS = {'.py', '.ts', '.tsx', '.yaml', '.yml', '.tf', '.sh', '.json', '.html', '.md'}

def bundle_project(root_dir, output_file):
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("# CONTEXTE TECHNIQUE DU PROJET SKILLS_EXAMPLS;MD\n\n")
        f.write("Ce fichier consolide le code source pour analyse architecturale.\n\n")
        
        for root, dirs, files in os.walk(root_dir):
            # Filtrage des dossiers exclus
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            
            for file in files:
                if file in EXCLUDE_FILES:
                    continue
                
                ext = os.path.splitext(file)[1]
                # On prend les extensions valides ou les Dockerfiles
                if ext in VALID_EXTENSIONS or file == 'Dockerfile' or file == 'requirements.txt':
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, root_dir)
                    
                    f.write(f"## FICHIER : {rel_path}\n")
                    
                    # Choix de la coloration syntaxique Markdown
                    lang = ext.replace('.', '')
                    if file == 'Dockerfile': lang = 'dockerfile'
                    if lang in ['tf', 'tfvars']: lang = 'hcl'
                    if lang in ['yml', 'yaml']: lang = 'yaml'
                    
                    f.write(f"```{lang}\n")
                    try:
                        with open(full_path, 'r', encoding='utf-8', errors='ignore') as src:
                            f.write(src.read())
                    except Exception as e:
                        f.write(f"# Erreur de lecture du fichier : {str(e)}")
                    # Ligne corrigée ici sans retour à la ligne physique dans le code
                    f.write("\n```\n\n")

if __name__ == "__main__":
    bundle_project('.', 'ledgerhub-mobile-contxt.md')
    print("Succès : ledgerhub-mobile-contxt.md a été généré à la racine !")